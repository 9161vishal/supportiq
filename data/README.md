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
- **Phase 2B Step 3.1**: Completed. `LlmIntentClassifier` maps intents deterministically.
- **Phase 2B Step 3.2**: Completed. Historical candidate retrieval uses an extremely memory-efficient `TweetOffsetIndex` (mapping IDs to byte offsets) to instantly fetch and reconstruct conversational branches using a `CsvOffsetReader`, bypassing the need to re-scan `twcs.csv`. `HistoricalMappingPreparer` accurately maps historical interactions into verified directories using the LLM classifier on a small auditable validation sample, creating real (not synthesized) historical mapping ground truth.
- **Phase 2B Step 3.3**: Pending. No embeddings or similarity matching.
- Note: Phase 2B Step 3.3 and beyond have NOT started yet.
