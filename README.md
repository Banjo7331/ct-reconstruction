# CT Reconstruction

A Spring Boot service that reconstructs **real X-ray tomography measurements** with a parallel
iterative solver. The data comes from the open datasets of the Finnish Inverse Problems Society
(FIPS): a walnut and a lotus root scanned on a laboratory micro-CT. Each dataset contains the
measured sinogram `m` and the sparse system matrix `A` of the scanner, and the service solves the
Tikhonov-regularised problem

```
minimise ||A x - m||² + α ||x||²
```

with conjugate gradients, exactly like the `example.m` script published with the data, so results
can be compared with the dataset documentation.

## What it shows

- **Parallel computing on real data**: every iteration applies `A` and `Aᵀ` to a vector, sparse
  products with millions of non-zeros, plus dot products and vector updates. All of it runs
  sequentially, as static blocks on a thread pool, or on a fork/join pool, and every mode produces a
  bit-identical image.
- **Hexagonal architecture**: the domain and use cases live in a module with no dependencies.
  Spring, the solver and the MAT-file reader are adapters around it.
- **Concurrency without shared mutable state**: an immutable reconstruction aggregate updated
  atomically, cooperative cancellation, deterministic reductions and a bounded job queue that
  answers 503 instead of running out of memory.

## Modules

```
ct-matlab    reader for MATLAB Level 5 MAT-files: dense and sparse arrays (plain Java)
ct-engine    parallel sparse linear algebra and the conjugate gradient solver (plain Java)
ct-core      domain model, use cases and ports (plain Java, depends on nothing)
ct-service   Spring Boot: REST API, adapters, configuration, browser viewer
```

## Getting the data

The measurement files are not part of this repository. Download them once into a `data` folder
in the project root, one sub-folder per object.

**Walnut**, [Zenodo record 1254206](https://zenodo.org/records/1254206), licence CC BY 4.0:

| File | Size | MD5 |
|---|---|---|
| `Data82.mat` | 8.1 MB | `5698942708300bc34b87931c6d91f6b6` |
| `Data164.mat` | 33.2 MB | `71600ad279ef231eaaa269d78c447b85` |
| `Data328.mat` | 135.8 MB | `4172c77195df57e4c289b9f0f2440b82` |
| `GroundTruthReconstruction.mat` | 9.7 MB | `4730bc6421133b71b755fbd892a8e515` |

**Lotus root**, from the [FIPS open datasets page](https://fips.fi/open-datasets/x-ray-tomographic-datasets/):
`Data128.mat`, `Data256.mat` and optionally `GroundTruthReconstruction.mat`.

```
data/
├── walnut/
│   ├── Data82.mat
│   ├── Data164.mat
│   └── GroundTruthReconstruction.mat
└── lotus/
    ├── Data128.mat
    └── GroundTruthReconstruction.mat
```

```bash
md5sum data/walnut/*.mat      # on macOS: md5 data/walnut/*.mat
```

Any MAT-file in the folder that holds a sparse `A` and a sinogram `m` with
`rows(A) = numel(m)` is recognised as a dataset. A `GroundTruthReconstruction.mat` next to it is
shown as a reference. Files saved in MATLAB's HDF5-based format (`-v7.3`) are not supported.

## Running

Requirements: JDK 21 and Maven 3.9.

Run both commands from the project root, the folder that contains `data/`:

```bash
mvn verify                                   # build and run all tests
java -jar ct-service/target/ct-service.jar   # start on http://localhost:8080
```

The data folder defaults to `./data` relative to the working directory. In IntelliJ, set the
working directory of the run configuration to the project root (`$PROJECT_DIR$`). Point it
elsewhere with `--ct.data-directory=/path/to/data` or the `CT_DATA_DIRECTORY` environment variable.

The performance test is excluded from the normal build. Run it on a machine with at least four
cores with:

```bash
mvn -Pperformance -pl ct-engine -am verify
```

With Docker, the `data` folder is mounted read-only:

```bash
docker compose up --build
```

Open http://localhost:8080, pick a dataset and start a reconstruction.

## API

| Method | Path | Description |
|---|---|---|
| GET | `/api/datasets` | Datasets found in the data folder |
| GET | `/api/datasets/sinogram?id=walnut/Data164.mat` | Measured sinogram as PNG |
| GET | `/api/datasets/reference?id=walnut/Data164.mat` | Reference reconstruction as PNG |
| POST | `/api/reconstructions` | Start a reconstruction, `202 Accepted` with `Location` |
| GET | `/api/reconstructions` | All reconstructions, newest first |
| GET | `/api/reconstructions/{id}` | Status, progress and convergence history |
| POST | `/api/reconstructions/{id}/cancel` | Stop at the next iteration |
| GET | `/api/reconstructions/{id}/image` | Result as PNG, optional `windowMin` and `windowMax` |

```bash
curl -i -X POST localhost:8080/api/reconstructions -H 'Content-Type: application/json' \
  -d '{"datasetId": "walnut/Data164.mat", "iterations": 20, "regularization": 10, "mode": "FORK_JOIN", "threads": 8}'
```

Only `datasetId` is required. Errors are RFC 9457 problem details.

## Benchmark

```bash
mvn -pl ct-engine -am -DskipTests package
java -cp ct-engine/target/classes dev.ctrecon.engine.benchmark.SolverBenchmark 192 180 30
```

Arguments are image size, number of angles and iterations of a synthetic scan. The benchmark warms
up the JIT, then reports the median of three runs for every mode and thread count.

## Test fixtures

`tools/make_mat_fixtures.py` regenerates the small MAT-files used by the tests (requires NumPy and
SciPy). The tests never need the real downloads.

## Data credits

- K. Hämäläinen, L. Harhanen, A. Kallonen, A. Kujanpää, E. Niemi and S. Siltanen,
  *Tomographic X-ray data of a walnut*, arXiv:1502.04064, data DOI 10.5281/zenodo.1254206.
- T. A. Bubba, A. Hauptmann, S. Huotari, J. Rimpeläinen and S. Siltanen,
  *Tomographic X-ray data of a lotus root filled with attenuating objects*, arXiv:1609.07299.
