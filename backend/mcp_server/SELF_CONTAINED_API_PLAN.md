# HealthOS V2 API ownership

`gops86-ctrl/healthos2` is the HealthOS V2 service. `gopalmahesh86-beep/Healthos` is V1 and is not the runtime dependency for V2 MCP data reads.

The V2 service must own the canonical read API consumed by the MCP tools, including metrics, activities, strength/workouts, nutrition, body/weight, labs, profile, and data-source information.

The current V2 MCP tools call these canonical read paths. This marker file records the intended ownership while the data/API layer is implemented in this repository.
