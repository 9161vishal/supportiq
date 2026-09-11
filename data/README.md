# SupportIQ Data

- `data/raw/twcs.csv` is the original source of truth.
- It is intentionally not committed to version control because of its size.
- `data/mapping` contains lightweight ID-only mappings.
- The mapping hierarchy is as follows:
  ```text
  AmazonHelp
    -> MainCategory
        -> SubCategory
            -> mapping.jsonl
  ```
- Tweet text is always resolved directly from the original CSV.
- The original dataset is never modified.
- **Phase 2B Step 1 Complete**: The `intermediate_paths.jsonl` file has been generated containing ID-only structural paths for AmazonHelp interactions.
- **Phase 2B Step 2 Complete (Intent Discovery)**: The `data/analysis/intent-discovery` folder contains heuristic validation reports extracted directly from the raw CSV using a bounded-memory scanner. This provides evidence for taxonomy validation.
- **Phase 2B Step 3.1 Complete (AI Intent Classification)**: A lightweight semantic classifier (`LlmIntentClassifier`) maps customer queries directly to `IntentTaxonomy`. No new datasets were created, and unit tests do not require external LLM access.
- Note: Phase 2B Step 3.2 (Historical Retrieval) and beyond have NOT started yet.
