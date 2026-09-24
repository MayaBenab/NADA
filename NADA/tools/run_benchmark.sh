#!/bin/sh
# Scalability experiment: generates synthetic policies of increasing size and
# times Algorithms 1-5 on each of them. Writes bench/results.csv.
#
#   sh tools/run_benchmark.sh
#
# Requires python3 and maven (the same setup as run.sh).
set -e
cd "$(dirname "$0")/.."

SIZES="100 200 500 1000 2000 5000"
mkdir -p bench
ARGS=""
for n in $SIZES; do
    python3 tools/generate_policy.py --rules "$n" --depth 5 --branching 3 \
        --out "bench/synth$n/policy.json"
    ARGS="$ARGS bench/synth$n/policy.json"
done

mvn -q compile exec:java \
    -Dexec.mainClass=com.example.ngac.support.Benchmark \
    -Dexec.args="$ARGS" | tee bench/results.csv

echo
echo "Results written to bench/results.csv"
