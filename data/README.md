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
- Note: Category and Subcategory classification (Phase 2B Step 2) has NOT started yet.
