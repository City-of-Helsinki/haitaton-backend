import math
from sqlalchemy import create_engine, text
from sqlalchemy.exc import ProgrammingError

def get_data_count(pg_conn_uri, counted_table, logger=None):
    data_amount = None
    sql = "select count(*) lkm from " + counted_table + " where geom is not null"
    engine = create_engine(pg_conn_uri)
    with engine.connect() as connection:
        try:
            count_result = connection.execute(text(sql)).fetchall()
        except ProgrammingError as e:
            data_amount = -1
            count_result = None
            logger.error(f"Error: {e}")
        except Exception as e:
            count_result = None
            logger.error(f"Error: {e}")

    if count_result:
        for row in count_result:
            data_amount = row.lkm

    return data_amount

def validate_data_count_limits(module, pg_conn_uri, tormays_table_org, tormays_file_temp, validate_limit_min, validate_limit_max, filename, logger=None):
    # validate data amount: is it between given limits
    textval = None
    old_amount = get_data_count(pg_conn_uri, tormays_table_org, logger)
    new_amount = len(tormays_file_temp)

    if new_amount and old_amount and validate_limit_min and validate_limit_max and old_amount != -1:
        min_limit = math.floor(validate_limit_min*old_amount)
        max_limit = math.ceil(validate_limit_max*old_amount)
        if new_amount >= min_limit and new_amount <= max_limit:
            textval = "Valid"
        elif new_amount < min_limit:
            textval = "Data amount is Below given limits"
        elif new_amount > max_limit:
            textval = "Data amount is Above given limits"
    else:
        min_limit = "NA"
        max_limit = "NA"
        if old_amount == -1:
            textval = "Tormays table {} not exists."
        else:
            textval = "Not valid"

    logger.info("Data amount validation started")
    logger.info("Module: " + module)
    logger.info("New data amount (" + filename + "): " + str(new_amount))
    logger.info("Old data amount (" + tormays_table_org + "): " + str(old_amount))
    logger.info("Limits: " + str(min_limit) + " <= new amount <= " + str(max_limit))

    if textval == "Valid":
        logger.info("Data amount validation result: " + textval)
        return True
    elif textval == "Tormays table {} not exists.":
        logger.error(textval.format(tormays_table_org))
        return False
    else:
        logger.error("Data amount validation result: " + textval)
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
            logger.info(f"Uploaded new data into table {tormays_table_org}: {len(tormays_file_temp)} rows")
        except Exception as e:
            # Transaction implicitly rolls back if an exception occurred within the "with" block
            logger.error(f"Error: {e}")