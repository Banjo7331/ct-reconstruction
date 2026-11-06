"""Generates the MAT-file fixtures used by the ct-matlab tests.

Two kinds of files are produced:
  * files written by scipy.io.savemat, compressed and uncompressed, the way
    MATLAB -v7 and -v6 write them;
  * files written byte by byte by the small writer below, to cover what scipy
    never produces but MATLAB does: a double array stored in a narrower integer
    type, small data elements, and big-endian byte order.

Usage: python3 tools/make_mat_fixtures.py ct-matlab/src/test/resources/fixtures
"""
import struct
import sys
from pathlib import Path

import numpy as np
import scipy.io
import scipy.sparse

MI_INT8, MI_UINT8, MI_INT16, MI_INT32, MI_UINT32, MI_DOUBLE, MI_MATRIX = 1, 2, 3, 5, 6, 9, 14
MX_CHAR, MX_SPARSE, MX_DOUBLE = 4, 5, 6


def system_variables():
    sparse = scipy.sparse.csc_matrix(np.array([
        [1.5, 0.0, 0.0, 2.0],
        [0.0, 0.0, 3.25, 0.0],
        [0.0, 4.0, 0.0, 0.0],
        [0.5, 0.0, 0.0, 0.0],
        [0.0, 0.0, 0.0, 0.0],
        [0.0, 6.0, 7.0, 8.0],
    ]))
    measurements = np.array([[0.1, 0.4], [0.2, 0.5], [0.3, 0.6]])
    return {
        "A": sparse,
        "m": measurements,
        "normA": np.array([[12.5]]),
        "desc": "fixture with every kind of variable",
        "meta": {"scanner": "none", "angles": np.array([[0.0, 90.0]])},
    }


def element(order, data_type, payload, allow_small=True):
    size = len(payload)
    if allow_small and size <= 4:
        return struct.pack(order + "I", (size << 16) | data_type) + payload.ljust(4, b"\0")
    padding = (8 - size % 8) % 8
    return struct.pack(order + "II", data_type, size) + payload + b"\0" * padding


def matrix(order, name, mx_class, dims, parts, nzmax=0):
    flags = element(order, MI_UINT32, struct.pack(order + "II", mx_class, nzmax), allow_small=False)
    dimensions = element(order, MI_INT32, struct.pack(order + "%di" % len(dims), *dims))
    body = flags + dimensions + element(order, MI_INT8, name.encode("ascii")) + b"".join(parts)
    return struct.pack(order + "II", MI_MATRIX, len(body)) + body


def header(order):
    text = b"MATLAB 5.0 MAT-file, written by ct-reconstruction test fixtures".ljust(116, b" ")
    return text + b"\0" * 8 + struct.pack(order + "H", 0x0100) + struct.pack(order + "H", (ord("M") << 8) | ord("I"))


def handwritten(order):
    narrow = matrix(order, "m", MX_DOUBLE, [3, 2],
                    [element(order, MI_UINT8, bytes([0, 1, 2, 250, 251, 255]))])
    signed = matrix(order, "x", MX_DOUBLE, [2, 2],
                    [element(order, MI_INT16, struct.pack(order + "4h", -3, 7, -32768, 32767))])
    ir = struct.pack(order + "3i", 0, 2, 1)
    jc = struct.pack(order + "3i", 0, 2, 3)
    pr = struct.pack(order + "3d", 0.25, -1.0, 9.5)
    sparse = matrix(order, "A", MX_SPARSE, [3, 2],
                    [element(order, MI_INT32, ir), element(order, MI_INT32, jc), element(order, MI_DOUBLE, pr)],
                    nzmax=3)
    text = matrix(order, "desc", MX_CHAR, [1, 2],
                  [element(order, MI_UINT8, b"ok")])
    return header(order) + narrow + signed + sparse + text


def main(target):
    target = Path(target)
    target.mkdir(parents=True, exist_ok=True)
    scipy.io.savemat(target / "system-compressed.mat", system_variables(), do_compression=True, format="5")
    scipy.io.savemat(target / "system-uncompressed.mat", system_variables(), do_compression=False, format="5")
    (target / "handwritten-little-endian.mat").write_bytes(handwritten("<"))
    (target / "handwritten-big-endian.mat").write_bytes(handwritten(">"))
    (target / "not-a-mat-file.mat").write_bytes(b"This is plain text, not a MAT-file." * 5)


if __name__ == "__main__":
    main(sys.argv[1])
