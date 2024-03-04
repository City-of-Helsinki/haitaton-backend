import math

from sqlalchemy import create_engine, text
from sqlalchemy.exc import ProgrammingError


def get_data_count(pg_conn_uri, counted_table, logger=None):
    data_amount = None
    sql = f"select count(*) lkm from {counted_table} where geom is not null"
    engine = create_engine(pg_conn_uri)
    with engine.connect() as connection:
        try:
            count_result = connection.execute(text(sql)).fetchall()
        except ProgrammingError:
            data_amount = -1
            count_result = None
            logger.exception("Error")
        except Exception:
            count_result = None
            logger.exception("Error")

    if count_result:
        for row in count_result:
            data_amount = row.lkm

    return data_amount


def validate_data_count_limits(
    module,
    pg_conn_uri,
    tormays_table_org,
    tormays_file_temp,
    validate_limit_min,
    validate_limit_max,
    filename,
    logger=None,
):
    """validate data amount: is it between given limits"""
    logger.info("Data amount validation started")
    logger.info("Module: %s", module)
    old_amount = get_data_count(pg_conn_uri, tormays_table_org, logger)
    logger.info("Old data amount (%s): %s", tormays_table_org, old_amount)
    new_amount = len(tormays_file_temp)
    logger.info("New data amount %s: %s", filename, new_amount)
    if new_amount and old_amount and validate_limit_min and validate_limit_max and old_amount != -1:
        min_limit = math.floor(validate_limit_min * old_amount)
        max_limit = math.ceil(validate_limit_max * old_amount)
        logger.info("Limits: %s <= new amount <= %s", min_limit, max_limit)
        if new_amount < min_limit:
            logger.error("Data amount validation failed: Data amount is Below given limits")
            return False
        if new_amount > max_limit:
            logger.error("Data amount validation failed: Data amount is Above given limits")
            return False
        logger.info("Data amount is within given limits")
        return True
    if old_amount == -1:
        logger.error("Tormays table %s not exists.", tormays_table_org)
        return False
    logger.error("Data amount validation failed because of missing configuration.")
    return False


def deploy(pg_conn_uri, tormays_table_org, tormays_file_temp, logger=None):
    engine = create_engine(pg_conn_uri)
    with engine.connect() as connection:
        transaction = connection.begin()
        try:
            logger.info("Deploy ...")
            tormays_file_temp.to_postgis(
                tormays_table_org,
                connection,
                "public",
                if_exists="replace",
                index=True,
                index_label="fid",
            )
            transaction.commit()
            logger.info("Uploaded new data into table %s: %s rows", tormays_table_org, len(tormays_file_temp))
        except Exception:
            # Transaction implicitly rolls back if an exception occurred within the "with" block
            logger.exception("Error")
