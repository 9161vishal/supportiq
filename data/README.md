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
- Note: Mapping files have not yet been generated. They will be generated in Phase 2B.
