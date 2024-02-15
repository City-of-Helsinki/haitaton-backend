import math
from sqlalchemy import create_engine, text
    
def deploy(pg_conn_uri, tormays_table_org, tormays_table_temp, logger=None):
    # validate data amount: is it between given limits
    delete_result = None
    engine = create_engine(pg_conn_uri)
    with engine.connect() as connection:
        transaction = connection.begin()
        delete_sql = "delete from " + tormays_table_org
        try:
            logger.info("Deploy ...")
            # Future development: Add JDBC Lock Registry functionality by inserting line in the table "int_lock" before actual deleting transaction and removing lock line after insert transaction.
            delete_result = connection.execute(text(delete_sql))
            logger.info(tormays_table_org + ": " + f"{delete_result.rowcount} rows deleted.")
        except Exception as e:
            # Transaction implicitly rolls back if an exception occurred within the "with" block
            logger.error(f"Error: {e}")
        
        if delete_result:    
            copy_sql = "insert into " + tormays_table_org + " select * from " + tormays_table_temp
            try:
                copy_result = connection.execute(text(copy_sql))
                transaction.commit()
                logger.info(tormays_table_org + ": " + f"{copy_result.rowcount} rows inserted.")
            except Exception as e:
                # Transaction implicitly rolls back if an exception occurred within the "with" block
                logger.error(f"Error: {e}")
            
def validate_data_count_limits(module, pg_conn_uri, tormays_table_org, tormays_table_temp, validate_limit_min, validate_limit_max, logger=None):
    # validate data amount: is it between given limits
    tormays_result = None
    temp_result = None
    retval = None
    old_amount = None
    new_amount = None
    tormays_sql = "select count(*) lkm from " + tormays_table_org + " where geometry is not null"
    temp_sql = "select count(*) lkm from " + tormays_table_temp + " where geometry is not null"
    engine = create_engine(pg_conn_uri)
    with engine.connect() as connection:
        try:
            tormays_result = connection.execute(text(tormays_sql)).fetchall()
        except Exception as e:
            # Transaction implicitly rolls back if an exception occurred within the "with" block
            logger.error(f"Error: {e}")

    with engine.connect() as connection:
        try:
            temp_result = connection.execute(text(temp_sql)).fetchall()
        except Exception as e:
            # Transaction implicitly rolls back if an exception occurred within the "with" block
            logger.error(f"Error: {e}")

    if tormays_result:
        for row in tormays_result:
            old_amount = row.lkm
    
    if temp_result:
        for row in temp_result:
            new_amount = row.lkm
    
    if new_amount and old_amount:
        if new_amount>=math.floor(validate_limit_min*old_amount) and new_amount<=math.ceil(validate_limit_max*old_amount):
            retval = "Valid"
        elif new_amount<math.floor(validate_limit_min*old_amount):
            retval = "Data amount is Below given limits"
        elif new_amount>math.ceil(validate_limit_max*old_amount):
            retval = "Data amount is Above given limits"
        else:
            retval = "Not valid"
    else:
        retval = "Not valid"

    logger.info("Data amount validation started")
    logger.info("Module: " + module)
    logger.info("New data amount (" + tormays_table_temp + "): " + str(new_amount))
    logger.info("Old data amount (" + tormays_table_org + "): " + str(old_amount))
    if old_amount and validate_limit_min and validate_limit_max:
        low_limit = math.floor(validate_limit_min*old_amount)
        max_limit = math.ceil(validate_limit_max*old_amount)
    else:
        low_limit = "NA"
        max_limit = "NA"
        
    logger.info("Limits: " + str(low_limit) + " <= new amount <= " + str(max_limit))
    
    if retval == "Valid":
        logger.info("Data amount validation result: " + retval)
    else:
        logger.error("Data amount validation result: " + retval)
        
    return retval
