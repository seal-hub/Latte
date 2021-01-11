import logging

logger = logging.getLogger("transdroid")
logger.setLevel(logging.INFO)
stdh = logging.StreamHandler()
formatter = logging.Formatter("[%(asctime)s][%(filename)15s:%(lineno)4s][%(levelname)5s] %(message)s")
stdh.setFormatter(formatter)
logger.addHandler(stdh)