# NADA — NGAC Anomaly Detection Analyzer

NADA is a desktop tool that builds the **complemented graph** `G_Com` of an NGAC policy (the
policy graph in which every prohibition is added as edges) and detects three kinds of anomalies
in it: full/partial redundancies, hierarchical redundancies and conflicts. It is built on NIST's
reference implementation of the NGAC Policy Machine, `policy-machine-core`.

---

## Overview

Getting NADA running takes three steps. Steps 1 and 2 are done **once per computer**; step 3 is
what you do **every time** you want to use NADA.

| Step | What | How often |
|---|---|---|
| 1 | Install Java, Maven and Git | once |
| 2 | Download and install NIST's `policy-machine-core` library | once |
| 3 | Start NADA | every time |

You will work with **two different folders**. Keep them apart:

```
policy-machine-core\    NIST's library   → used only in step 2
NADA\           this tool        → used in step 3
```

Both folders contain a file named `pom.xml`. Always check which folder your terminal is in
before typing a command (the folder is shown at the start of the line, e.g.
`PS C:\Users\you\NADA>`).

All commands below are for **Windows PowerShell**, the default terminal of Windows 10/11.
To open it: click *Start*, type `powershell`, press Enter. Type the commands **one at a time**
and wait for each one to finish before typing the next. macOS/Linux commands are given after
each Windows block.

---

## Step 1 — Install Java, Maven and Git (once)

| Tool | Where to get it | Notes |
|---|---|---|
| Java JDK **21** or newer | <https://adoptium.net> (Temurin 21) | In the installer, enable *Set JAVA_HOME* and *Add to PATH*. |
| Maven 3.9 or newer | <https://maven.apache.org/download.cgi> (binary zip) | Unzip it, then add its `bin` folder to your `PATH`. |
| Git | <https://git-scm.com/downloads> | Default options are fine. |
| Graphviz (optional) | <https://graphviz.org/download/> | Gives a cleaner graph layout. NADA works without it. |

**Check.** Close any open terminal, open a **new** PowerShell and type:

```
java -version
mvn -v
git --version
```

Each command must print a version number, and `java -version` must show **21** or higher.
If one of them says *"not recognized"*, that tool is not installed or not on your `PATH`.

---

## Step 2 — Install `policy-machine-core` (once)

NADA needs version **4.0.1-rc.1** of NIST's library. It is not available online as a ready
package, so you build it once on your computer.

**2.1 Download it.** In PowerShell:

```
cd $HOME
git clone https://github.com/usnistgov/policy-machine-core.git
```

If you see *"destination path 'policy-machine-core' already exists"*, it was already downloaded:
just continue.

**2.2 Go into its folder.**

```
cd $HOME\policy-machine-core
```

**2.3 Check the version.**

```
Select-String -Path pom.xml -Pattern "<version>" | Select-Object -First 1
```

The line shown must contain `4.0.1-rc.1`. If it shows another version, type
`git checkout ae742507` to go back to the version NADA was checked against.

**2.4 Build and install it.**

```
.\mvnw.cmd -pl core -am install -DskipTests
```

Type the `.\` at the beginning: PowerShell needs it to run a script from the current folder.
The first build downloads what it needs and can take a few minutes. It is finished when the
last lines show **`BUILD SUCCESS`**.

*macOS / Linux:*

```
cd ~
git clone https://github.com/usnistgov/policy-machine-core.git
cd ~/policy-machine-core
./mvnw -pl core -am install -DskipTests
```

You will not need the `policy-machine-core` folder again, except to rebuild it.

---

## Step 3 — Start NADA (every time)

**3.1 Unzip NADA** (if not done yet): right-click `NADA.zip` → *Extract All…*.

**3.2 Open a terminal in the NADA folder.** The simplest way:

1. Open the `NADA` folder in File Explorer (the folder that contains `run.bat` and
   `case_studies`).
2. Click in the address bar at the top, type `powershell`, press Enter.

A PowerShell window opens, already in the right folder. Check that the line starts with
`...\NADA>` and **not** with `...\policy-machine-core>`.

(Or type `cd` followed by the folder's path, e.g. `cd $HOME\Desktop\NADA`; if your
Desktop is synchronized with OneDrive, `cd $HOME\OneDrive\Desktop\NADA`.)

**3.3 Start NADA.**

```
.\run.bat
```

You can also simply double-click `run.bat` in File Explorer. If Windows shows *"Windows protected
your PC"*, click *More info* → *Run anyway*. The first start downloads a few libraries and can
take a minute; keep the console window open while you use NADA.

*macOS / Linux:* `cd` to the NADA folder, then `sh run.sh`.

**3.4 Use NADA.**

1. Select a case study in the tree on the left (`Drone`, `HierarchyDemo`, `LawFirm` or
   `Campus`), or load
   your own file with *File → Import Policy (JSON, external file)*.
2. Click the toolbar buttons in this order:
   **1. Compute & Visualize FP** → **2. Compute & Visualize G_Com** → **3. Detect Anomalies**.
3. The results window lists every anomaly found, with its pattern number and its diagram.
   *Export PNG* and *Export JSON* save the current graph (the PNG is always the whole graph at
   100%, whatever the zoom on screen).

**Zooming and moving the graph.**

| Action | How |
|---|---|
| Zoom in / out | **Ctrl + mouse wheel** (zooms around the mouse pointer), the **+** / **−** toolbar buttons, or **Ctrl + +** / **Ctrl + −** |
| Back to 100% | **100%** toolbar button, or **Ctrl + 0** |
| See the whole graph | **Fit** toolbar button, or **Ctrl + 9** |
| Move the view | Click and drag the graph (the mouse wheel alone still scrolls) |

The same commands are in the **View** menu, and the current zoom level is shown in the toolbar.

**3.5 Check your installation.** The four bundled case studies must give:

| Case study | Alg. 3 (full/partial) | Alg. 4 (hierarchical) | Alg. 5 (conflicts) |
|---|---|---|---|
| `Drone` | 0 | 1 (1 permission, 0 prohibition) | 2 (Patterns 12, 12) |
| `LawFirm` | 0 | 0 | 6 (Patterns 16 ×3, 12 ×2, 13) |
| `Campus` | 3 prohibition-side (1 full, 2 partial) | 5 (2 permission, 3 prohibition) | 6 (Patterns 9, 10, 11, 14, 15, 17) |

`Campus` is a synthetic coverage policy (a small university department): `Drone` and `LawFirm`
instantiate Patterns 3, 12, 13 and 16, and `Campus` instantiates every remaining pattern that can occur in a single policy
file — Pattern 2 (prohibition-side full and partial redundancy), Patterns 4–8 (hierarchical
redundancy) and Patterns 9, 10, 11, 14, 15, 17 (the remaining conflict configurations). It also
contains one inactive association, one inactive prohibition, and one association that is redundant
in policy class `Zoo` while remaining necessary in policy class `Two`. Pattern 1 cannot occur here:
the PM core keeps at most one association per `(ua, at)` pair.

---

## 4. Writing your own policy (`policy.json`)

A policy is one JSON file, written in the JSON schema of the NGAC reference implementation
(`core/src/main/resources/json/pm.schema.json`) — the format produced by

```java
String json = pap.serialize(new JSONSerializer());
```

so a configuration exported from a running deployment is analysed as is. This is the format of
the four case studies under `case_studies/`.

```json
{
  "resourceAccessRights": ["drive", "read"],
  "graph": {
    "pcs":     [ {"id": 1, "name": "Driving"} ],
    "uas":     [ {"id": 2, "name": "Trainer", "assignments": [1],
                  "associations": [ {"target": 3, "arset": ["read"]} ] } ],
    "oas":     [ {"id": 3, "name": "Emergency", "assignments": [1]} ],
    "users":   [ {"id": 4, "name": "Ali", "assignments": [2]} ],
    "objects": [ {"id": 5, "name": "d2", "assignments": [3]} ]
  },
  "prohibitions": [
    {
      "name": "Proh_1",
      "subject": {"node": 2},
      "containers": [ {"id": 3, "complement": false} ],
      "arset": ["drive"],
      "intersection": true
    }
  ],
  "obligations": [],
  "operations": {"resourceOperations": [], "adminOperations": []}
}
```

- Every policy element has a numeric `id`, unique in the file, and a `name`.
- A node's `assignments` lists the ids of the containers it is assigned to, so each of them
  *contains* the node. Every node other than a policy class needs at least one.
- A user attribute's `associations` grants `arset` on the target: this is a permission rule.
- A prohibition denies `arset` to its `subject` on the containers listed in `containers`.
  `complement: false` is an inclusion container, `complement: true` an exclusion container, and
  `intersection` is the conjunctive reading (`true` by default).
- `obligations` and `operations` are read but ignored: they change the graph at run time and fall
  outside the scope of a static analysis. A prohibition whose subject is a process rather than a
  node is rejected, since a rule's subject must be in `U ∪ UA`.

To add a case study, create `case_studies/<Name>/policy.json` and use
*File → Refresh Case Study Tree*.

### 4.1 Readable, name-based form

Numeric ids are convenient for a machine and unreadable for a person, so NADA also accepts the
same model written with names. The format is detected automatically — a file with a `graph` key
is read as the reference schema, otherwise as the form below:

```json
{
  "nodes": [
    {"name": "Ali",       "type": "U",  "properties": {}},
    {"name": "Trainer",   "type": "UA", "properties": {}},
    {"name": "Emergency", "type": "OA", "properties": {}},
    {"name": "Driving",   "type": "PC", "properties": {}}
  ],
  "assignments":  [ {"source": "Ali", "target": "Trainer"} ],
  "associations": [ {"source": "Trainer", "target": "Emergency", "operations": ["read"]} ],
  "prohibitions": [
    {"name": "Proh_1", "subject": "Trainer", "accessRights": ["drive"],
     "inclusion": ["Emergency"], "exclusion": [], "conjunctive": true}
  ]
}
```

| Reference schema | Name-based form |
| --- | --- |
| `graph.pcs / uas / oas / users / objects` | `nodes` with a `type` in `{U, UA, O, OA, PC}` |
| node's `assignments: [id, ...]` | `assignments: [{"source", "target"}]` |
| ua's `associations: [{"target", "arset"}]` | `associations: [{"source", "target", "operations"}]` |
| numeric `id` | `name` |
| prohibition `arset` | `accessRights` |
| prohibition container with `"complement": false` | `inclusion` |
| prohibition container with `"complement": true` | `exclusion` |
| prohibition `intersection` (default `true`) | `conjunctive` (default `true`) |

`tools/to_pm_schema.py` rewrites a name-based file into the reference schema:

```
python3 tools/to_pm_schema.py case_studies/Drone/policy.json -o drone_pm.json
```

The prohibition style used by POMA — `ops`, `intersection`, and `containers` as a
`{"name": complement}` map — is accepted as well, so a POMA policy file can be used directly.

---

## 5. Definitions and algorithms (as in the paper)

Notation: `ua` user attribute, `at` target (attribute), `s` prohibition subject, `ars` set of
access rights, `PE` policy elements, `PC` policy classes.

### Containment — used by every algorithm

`contains(x, y)` means *x contains y*:

1. `contains(x, x)` for any `x ∈ U ∪ UA ∪ OA ∪ PC`;
2. `contains(x, y)` if `(y, x) ∈ ASSIGNMENT`;
3. `contains(x, z)` if `contains(x, y)` and `contains(y, z)`.

In the graph induced by the assignment relation, `contains(x, y)` holds iff there is a (possibly
empty) path from `y` to `x`.

### Prohibitions and target prohibition sets

`PROHIBITION ⊆ (U ∪ UA) × 2^AR × 2^(UA ∪ U ∪ OA)`. Each prohibition is a triple `(s, ars, tps)`,
also written `⟨s, ars, τ⟩`, where `tps` is the target prohibition set denoted by the target
prohibition specification `τ = ⟨op, (α₁, c₁), …, (αₙ, cₙ)⟩` (or `τ = (α₁, c₁)` if `n = 1`), with
`op ∈ {conjunctive, disjunctive}`, `αᵢ ∈ {inclusion, exclusion}` and `cᵢ ∈ UA ∪ OA ∪ PC`.

`ρ(αᵢ, cᵢ)` is taken within `Univ(cᵢ)` = `UA ∪ U` if `cᵢ ∈ UA`, `OA` if `cᵢ ∈ OA`,
`UA ∪ U ∪ OA` if `cᵢ ∈ PC`:

- `ρ(inclusion, c)` = the elements of `Univ(c)` contained by `c`;
- `ρ(exclusion, c)` = the elements of `Univ(c)` not contained by `c`.

`tps = ρ(α₁, c₁)` if `n = 1`; `⋂ᵢ ρ(αᵢ, cᵢ)` if `n > 1` and `op = conjunctive`;
`⋃ᵢ ρ(αᵢ, cᵢ)` if `n > 1` and `op = disjunctive`.

### Algorithms 1–2 — Complemented graph

**Definition (Complemented Graph).** `G_Com = ⟨PE, E⟩` with
`E = ⟨ASSIGNMENT, ASSOCIATION, FP⟩`, where `FP` is a multiset over
`(UA ∪ U ∪ OA) × 2^AR × (U ∪ UA)`. The topmost elements of `tps` are

`top(tps) = { x ∈ tps | ∄ y ∈ tps \ {x} : contains(y, x) }`

and `FP = ⊎_{⟨s, ars, τ⟩ ∈ PROHIBITION} { (at, ars, s) | at ∈ top(tps) }`: one edge per
prohibition and per topmost element, oriented from the denied element `at` toward the subject `s`.
Since `⊎` is multiset union, two distinct prohibitions that yield the same edge contribute two
elements of `FP`.

- **Algorithm 1** (`computeFP`): for each `⟨s, ars, τ⟩`, `t ← ResolveTPS(τ)`, then
  `FP ← FP ⊎ {(at, ars, s)}` for each `at ∈ t`.
- **Algorithm 2** (`resolveTPS`): computes `tps` from `τ` through `ρ`, then keeps its
  containment-maximal elements.
- File: `Algorithm1_2_ComplementedGraphBuilder.java`. The transitive closure of `contains` is
  precomputed once, so each containment test takes constant time.

### Algorithm 3 — Full and partial redundancy (Patterns 1–2)

**Definition.** Let `p₁, p₂` be two distinct rules of the same type (both permission rules or
both prohibition rules).

- `p₁, p₂` are **fully redundant** iff `ua(p₁) = ua(p₂) ∧ at(p₁) = at(p₂) ∧ ars(p₁) = ars(p₂)`
  (or with `s` in place of `ua` for prohibitions);
- `p₁, p₂` are **partially redundant** iff `ua(p₁) = ua(p₂) ∧ at(p₁) = at(p₂) ∧ ars(p₁) ≠ ars(p₂)
  ∧ ars(p₁) ∩ ars(p₂) ≠ ∅` (or with `s` in place of `ua` for prohibitions).

A rule is **active** in a policy class `pc` when `pc` contains both of its endpoints; only active
rules are considered. For each rule `p₁` (association, then `FP` edge), the algorithm computes
`PC₁ = {pc ∈ PC | contains(pc, ua(p₁)) ∧ contains(pc, at(p₁))}` (with `s` in place of `ua` for
prohibitions) and skips `p₁` if `PC₁ = ∅`. Otherwise it compares `p₁` with every `p₂` after it.
Since `p₁` and `p₂` share the same endpoints, `PC(p₂) = PC(p₁) = PC₁`: the redundancy holds in
every policy class of `PC₁` and never in only some of them, so each pair is reported **once**, as
`(V, E, pattern)` with `V = {ua(p₁), at(p₁)} ∪ PC₁` and `E = {p₁, p₂}` — Pattern 1 for
permissions, Pattern 2 for prohibitions. File: `Algorithm3_RedundancyDetector.java`.

### Algorithm 4 — Hierarchical redundancy (Patterns 3–8)

**Definition.** Let `p₁` and `p₂` be two distinct rules of the same type. `p₁` is hierarchically
redundant with respect to `p₂` if:

- **Permissions:** `contains(ua(p₂), ua(p₁)) ∧ contains(at(p₂), at(p₁)) ∧ ars(p₁) ⊆ ars(p₂)`;
- **Prohibitions:** `contains(s(p₂), s(p₁)) ∧ contains(at(p₂), at(p₁)) ∧ ars(p₁) ⊆ ars(p₂)`.

For each rule `p₂`, the algorithm computes `PC₂ = {pc ∈ PC | contains(pc, ua₂) ∧ contains(pc, at₂)}`
(with `s₂` for prohibitions) and skips `p₂` if `PC₂ = ∅`. Otherwise it builds
`Red₂ = {p₁ | contains(ua₂, ua₁) ∧ contains(at₂, at₁) ∧ ars₁ ⊆ ars₂ ∧ (ua₁ ≠ ua₂ ∨ at₁ ≠ at₂)}`.
By transitivity of `contains`, every `p₁ ∈ Red₂` is active wherever `p₂` is (`PC(p₂) ⊆ PC(p₁)`), so
the redundancy holds exactly in `PC₂` and each pair is reported **once**, as `(V, E, pattern)` with
`V = {ua₂, at₂, ua₁, at₁} ∪ PC₂` and the ordered pair `E = (p₂, p₁)` (`p₁` is the redundant rule,
`p₂` the rule that covers it). The inclusion may be strict: `p₁` then stays necessary in
`PC(p₁) \ PC(p₂)`, so a hierarchically redundant rule may be removed only if it is covered in every
policy class in which it is active. Patterns (permissions / prohibitions): **3 / 6** neither
endpoint shared, **4 / 7** only `ua` / `s` shared, **5 / 8** only `at` shared.
File: `Algorithm4_HierarchicalRedundancyDetector.java`.

### Algorithm 5 — Conflicts (Patterns 9–17)

**Definition.** Let `perm = (ua(perm), ars(perm), at(perm))` be a permission rule and
`proh = (at(proh), ars(proh), s(proh))` a prohibition rule. They are in conflict if

```
(contains(ua(perm), s(proh)) ∨ contains(s(proh), ua(perm)))
∧ (contains(at(perm), at(proh)) ∨ contains(at(proh), at(perm)))
∧ ars(perm) ∩ ars(proh) ≠ ∅
```

Each side (subjects, targets) is in one of three configurations, which gives nine patterns:

| subjects \ targets | `at(perm) = at(proh)` | `contains(at(perm), at(proh))` | `contains(at(proh), at(perm))` |
|---|---|---|---|
| `ua(perm) = s(proh)` | 9 | 17 | 15 |
| `contains(ua(perm), s(proh))` | 16 | 12 | 13 |
| `contains(s(proh), ua(perm))` | 14 | 11 | 10 |

Only active rules are considered: for each prohibition rule the algorithm computes `PC_proh`
(the policy classes containing both `s(proh)` and `at(proh)`) and skips it if empty; for each
permission rule it keeps `PC_c = {pc ∈ PC_proh | contains(pc, ua(perm)) ∧ contains(pc, at(perm))}`.
A pair is reported only if `PC_c ≠ ∅`, once per `pc ∈ PC_c`, with
`V = {ua(perm), at(perm), s(proh), at(proh), pc}` and `E = {perm, proh}`.
File: `Algorithm5_ConflictDetector.java`.

---

## 5bis. Scalability experiment

`tools/generate_policy.py` generates a synthetic policy of a given size, and
`com.example.ngac.support.Benchmark` times Algorithms 1--5 on one or more policies:

```
sh tools/run_benchmark.sh          # generates bench/synth*/policy.json and writes bench/results.csv
                                   # (on Windows: double-click benchmark.bat)
```

The generator takes `--rules` (70% associations, 30% prohibitions), `--pc`, `--depth`,
`--branching`, `--rights` and `--seed`, and builds attribute hierarchies rooted in the policy
classes, with one user and one object per leaf. Rules are drawn within a policy class, so that
they are active in the sense of Definition (Rule). The benchmark runs 3 warm-up iterations and
reports the median of 7 timed runs, as CSV: build time (Algorithms 1--2), then the time and the
number of findings of each detection algorithm.

`bench/` ships the six policies used for the measurements of `bench/results.csv` (100 to 5000
rules over the same 1215-node graph, depth 5, branching factor 3), so the table of the paper can
be reproduced without regenerating them. They are written in the name-based form of Section 4.1,
which the generator produces; both forms are accepted, so this changes nothing to the
measurements.

## 6. Project layout

```
NADA
├── README.md, pom.xml, run.bat, run.sh
├── benchmark.bat                          scalability experiment (Windows)
├── bench/results.csv, synth100 ... synth5000/policy.json   scalability experiment data
├── case_studies/{Drone, HierarchyDemo, LawFirm, Campus}/policy.json
├── tools/generate_policy.py, run_benchmark.sh, to_pm_schema.py
└── src/
    ├── main/java/com/example/ngac/
    │   ├── NADA.java                                   GUI (entry point)
    │   └── support/
    │       ├── Algorithm1_2_ComplementedGraphBuilder.java   Algorithms 1–2
    │       ├── Algorithm3_RedundancyDetector.java           Algorithm 3
    │       ├── Algorithm4_HierarchicalRedundancyDetector.java Algorithm 4
    │       ├── Algorithm5_ConflictDetector.java             Algorithm 5
    │       ├── AnomalyDetector.java             runs Algorithms 3–5, builds the results
    │       ├── RedundancyPattern.java / ConflictPattern.java   pattern descriptions (1–17)
    │       ├── PatternDiagramPanel.java / ConflictPatternDiagramPanel.java   pattern diagrams
    │       ├── JsonPolicyLoader.java, JsonGraphLoader.java, JsonProhibitionsLoader.java
    │       ├── PmSchemaConverter.java            reference-implementation schema -> name-based
    │       ├── ContainmentIndex.java             transitive closure of contains, computed once
    │       ├── Benchmark.java                    scalability harness (writes bench/results.csv)
    │       └── GComGraphPanel.java, GComLayout.java, GComDotExporter.java   graph display/export
    └── test/java/com/example/ngac/GComDotExportTest.java   exports Drone's G_Com to gcom.dot
```

---

## 7. Troubleshooting

| What you see | Cause and fix |
|---|---|
| `'java'`, `'mvn'` or `'git'` *is not recognized* | The tool is not installed or not on `PATH` (step 1). Open a new terminal after installing. |
| `destination path 'policy-machine-core' already exists` | Already downloaded. Skip `git clone` and continue with step 2.2. |
| `The term 'mvnw.cmd' is not recognized` | Type `.\mvnw.cmd`, with `.\` in front (step 2.4). |
| Errors about `os-maven-plugin` or `grpc-bom` | You built the whole NIST project. Use exactly `.\mvnw.cmd -pl core -am install -DskipTests`. |
| `Could not find artifact gov.nist.ngac.pm:policy-machine-core:jar:4.0.1-rc.1` | Step 2 was not completed, or built another version. Redo steps 2.2 to 2.4 until `BUILD SUCCESS`. |
| `... on project policy-machine-core-parent` when starting NADA | Your terminal is in the `policy-machine-core` folder. Go to the NADA folder first (step 3.2). |
| `The term 'run.bat' is not recognized` | Type `.\run.bat`, and check that you are in the NADA folder. |
| `invalid target release: 21` / `release version 21 not supported` | Maven uses an older Java. Install Java 21 and set `JAVA_HOME` to it; `mvn -v` shows which Java Maven uses. |
| *"Windows protected your PC"* | Click *More info* → *Run anyway*. |
| The graph layout looks crowded | Install Graphviz (optional). |
