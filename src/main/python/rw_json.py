import os
import json


def read_json_file(filename):
    f = open(filename, "r")
    return json.load(f)


def write_json_file(obj, filename):
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    with open(filename, "w") as f:
        json.dump(obj, f, indent=4, sort_keys=True)
