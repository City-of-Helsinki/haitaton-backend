import pandas as pd
import numpy as np
import geopandas as gpd
from shapely.geometry import Point, LineString, shape
import matplotlib.pyplot as plt
import parse
from functools import partial

from sqlalchemy import create_engine
from sqlalchemy.dialects.postgresql import TEXT

from modules.config import Config
from modules.gis_processing import GisProcessor
from modules.hsl import HslBuses, current_week_dates, as_date, as_string

def create_gis_material(hsl : HslBuses, cfg : Config):
    connection = create_engine(cfg.pg_conn_uri("local_development"))
    # compute rush hours

    # compute HSL rush hours
    f_desc = hsl._rush_hours()
    pl = [(f[0], partial(hsl.agg_rush_hour,f[1],f[2])) for f in f_desc]

    service_ids_day = hsl.pick_service_ids_for_one_day(hsl.transit_day())
    trips_day = hsl.trips_for_service_ids(service_ids_day)
    stop_times_trip = hsl.feed().stop_times[hsl.feed().stop_times['trip_id'].isin(trips_day['trip_id'])]
    tmp = stop_times_trip.drop_duplicates('trip_id')
    tmp2 = tmp.merge(trips_day, on="trip_id", how="left")

    # rush hour time columns added
    kwargs = {nm: lambda x: pf(x.departure_time) for nm,pf in pl}
    tmp2 = tmp2.assign(**kwargs)

    shape_trips_max = tmp2.loc[:, ['shape_id'] + [x for x in tmp2.columns if x.startswith('n_')]].groupby('shape_id').sum().max(axis=1)
    stop_times_day = shape_trips_max.to_frame("rush_hour").reset_index()

    # pick weeks worth of service_ids
    w_start = hsl.transit_week()[0]
    w_end = hsl.transit_week()[-1]

    ind_service_ids_week = hsl.pick_service_ids(w_start, w_end)
    service_ids_week = hsl.feed().calendar[ind_service_ids_week]['service_id'].values.tolist()

    ind_trips_week = hsl.feed().trips['service_id'].isin(service_ids_week)
    trips_week = hsl.feed().trips[ind_trips_week]
    
    shps = hsl.feed().shapes.copy()
    shps['geometry'] = shps.apply(lambda r: Point(r.shape_pt_lon, r.shape_pt_lat), axis=1)
    shapes_week_ind = shps['shape_id'].isin(trips_week['shape_id'].tolist())
    shapes_week = shps[shapes_week_ind]

    shapes_week_lines = gpd.GeoDataFrame(shapes_week.groupby(['shape_id'])['geometry'].apply(lambda x: LineString(x.tolist())), geometry="geometry")
    sw_tmp1 = shapes_week_lines.merge(stop_times_day, on="shape_id", how="left").fillna(0)
    tw_tmp1 = trips_week.drop_duplicates("shape_id")
    sw_tmp2 = sw_tmp1.merge(tw_tmp1, on="shape_id", how="left")
    sw_tmp3 = sw_tmp2.merge(hsl.feed().routes, on="route_id", how="left")
    sw_tmp4 = sw_tmp3[~sw_tmp3.route_id.isna()]
    swa_tmp0 = sw_tmp4[~sw_tmp4.route_type.isin([0,1,4,109])]
    swa_tmp1 = hsl.add_trunk_descriptor(swa_tmp0)

    shapes_with_attributes = swa_tmp1
    shapes_with_attributes["fid"] = shapes_with_attributes.reset_index().index
    shapes_with_attributes = shapes_with_attributes.loc[:, ["fid", "route_id", "direction_id", "rush_hour", "trunk", "geometry"]]
    shapes_with_attributes = shapes_with_attributes.set_crs("EPSG:4326").to_crs("EPSG:3879")
    shapes_with_attributes.to_postgis("shapes_with_attributes", connection, "public", if_exists="replace", index=True)
    pass

if __name__ == "__main__":
    hsl = HslBuses(validate_gtfs=False)
    cfg = Config()
    create_gis_material(hsl, cfg)
    pass

