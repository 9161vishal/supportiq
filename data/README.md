# SupportIQ Data

- `data/raw` contains the original TWCS dataset.
- `twcs.csv` is the source of truth and must not be modified.
- `data/mapping` contains lightweight ID mappings.
- Mapping files do not duplicate tweet text.
- Mappings are organized by `AmazonHelp` -> category -> sub-category.
- Conversation relationships are preserved using original tweet IDs.
