#!/usr/bin/env python3
# MIT License
# 
# Copyright 2024 Broad Institute
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
"""Read one or more tab-separated files with header, and write a single tab-separated file with rows concatentated from the
input files.  The output columns will be the union of all the input columns, with empty values for input files that
lack a column.  """

import argparse
import sys
import pandas as pd

from . import logger, add_log_argument

def main(args=None):
    parser = argparse.ArgumentParser(description=__doc__)
    add_log_argument(parser)
    parser.add_argument("--index-col", "-i", default=None, action="append",
                        help="Column to use as index.  May be specified more than once.  If indices are not unique, "
                             "an error will be raised.")
    parser.add_argument("--output", "-o", default=sys.stdout, type=argparse.FileType('w'),
                        help="Output file.  Default: stdout.")
    parser.add_argument("input", nargs="+", type=argparse.FileType('r'),
                        help="Input tab-separated files.  May be specified more than once.")
    options = parser.parse_args(args)
    return run(options)

def get_best_column_types(dfs):
    """If a column has all empty values in a dataframe, it seems to get dtype float64.
    Look at all the columns across the input data frames, and if they column types disagree, pick the one
    for which there are non-empty values."""
    column_types = {}
    for df in dfs:
        for col in df.columns:
            if df[col].isna().all():
                continue
            if col not in column_types:
                column_types[col] = df[col].dtype
            elif df[col].dtype != column_types[col]:
                raise Exception(f"Column types disagree across input files for column {col}. {df[col].dtype} != {column_types[col]}")
    for col in dfs[0].columns:
        if col not in column_types:
            column_types[col] = dfs[0][col].dtype
    ret = df.dtypes.copy()
    for col in column_types:
        ret[col] = column_types[col]
    return ret

def maybe_fix_columns(df, dtypes):
    for col in df.columns:
        if col in dtypes:
            df[col] = df[col].astype(dtypes[col])
    return df

def run(options):
    dfs = [pd.read_csv(f, sep="\t") for f in options.input]
    map(lambda f: f.close(), options.input)
    best_dtypes = get_best_column_types(dfs)
    dfs = [maybe_fix_columns(df, best_dtypes) for df in dfs]

    df = pd.concat(dfs)
    if options.index_col:
        # Check for duplicate keys
        duplicate_keys = df[df.duplicated(subset=options.index_col, keep=False)]

        if not duplicate_keys.empty:
            logger.error(f"Duplicate keys found: {duplicate_keys[options.index_col]}")
            return 1

    df.to_csv(options.output, sep="\t", index=False)
    options.output.close()
    return 0

if __name__ == "__main__":
    sys.exit(main())
