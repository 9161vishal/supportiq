# SupportIQ

SupportIQ is an intelligent customer support agent system that analyzes and manages customer service interactions.

## Technology Stack
- **Language**: Java 21
- **Framework**: Spring Boot
- **Build Tool**: Maven

## High-Level Architecture
SupportIQ operates around a central `SupportAgentService` which orchestrates:
- **IntentClassifier**: Predicts the intent and category of inbound customer messages.
- **RetrievalService**: Looks up historical case precedents.
- **ResponseGenerator**: Generates appropriate support responses.
- **EscalationService**: Determines if human agent escalation is required.

Currently, these components are represented by interfaces as the project is in the foundation phase.

## Current Data Architecture
The data model revolves around the TWCS (Customer Support on Twitter) dataset. 
- Original records are read via a custom streaming `CsvReader`.
- Conversations are built into hierarchical paths using `ConversationBuilder` while ensuring determinism and cycle protection.
- ID-based structures are written to disk using `MappingWriter`.

### Setting up the Dataset
To run the data processing (when implemented), the raw dataset must be placed locally at:
`data/raw/twcs.csv`

**Note:** The raw dataset is ignored by Git due to its large size. Only lightweight ID-based mapping files will be stored.

## Current Development Status
- **Phase 1**: Completed (Architecture skeleton and contracts created).
- **Phase 2A**: Completed (Data and Intent Infrastructure finalized. File readers, builders, and validation are in place).
- **Phase 2B Step 1**: Completed. The full TWCS dataset (~500MB) was successfully processed using a primitive-memory graph to extract AmazonHelp interactions. The `intermediate_paths.jsonl` output has been generated. The raw CSV remains the immutable source of truth, and mappings remain purely ID-based.
- **Phase 2B Step 2**: Completed (Intent Discovery & Validation). A bounded-memory streaming analyzer extracted 82,534 initial customer messages and generated heuristic frequency and taxonomy validation reports. Evidence verified the 20-category taxonomy and identified a large number of multi-lingual/ambiguous cases without modifying the source dataset.
- **Phase 3.4**: Completed (AI Intent Classification). A lightweight, zero-dependency LLM classifier (`LlmIntentClassifier`) built on `java.net.http.HttpClient` maps customer intent directly to `IntentTaxonomy`. 
    - **Taxonomy**: `IntentTaxonomy` is the canonical source of truth for all categories and their exact allowed subcategories.
    - **Configuration**: Properties include `supportiq.classifier.api-url` and `supportiq.classifier.model` (default `gemini-3.6-flash`), and `supportiq.classifier.confidence-threshold` (default `0.6`). **Note:** Gemini is the default provider.
    - **Authentication**: The API key MUST be provided via the `SUPPORTIQ_AI_API_KEY` environment variable. E.g. `set SUPPORTIQ_AI_API_KEY=YOUR_KEY` or `$env:SUPPORTIQ_AI_API_KEY="YOUR_KEY"`. The actual secret must never be committed to Git.
    - **Confidence**: Model-provided score (0.0 - 1.0); not a calibrated statistical probability.
    - **Uncertainty & Fallbacks**: The LLM prompt explicitly provides the exact allowed subcategories and strictly prohibits inventing labels. The classifier safely parses and validates the combination: if the category/subcategory mapping is invalid or confidence is too low, it automatically falls back to a safe uncertain state `Intent(null, null, 0.0, true)`.
    - **Tests**: Unit tests do not require a live LLM API.
- **Phase 3.2**: Completed (Historical Retrieval). Efficiently retrieves historical interactions for a specific category and subcategory without repeatedly scanning the full dataset.
    - **Indexing**: An in-memory primitive hash map (`TweetOffsetIndex`) maps tweet IDs to byte offsets. This index takes only ~45MB of memory for all 2.8 million tweets.
    - **Retrieval**: `CsvOffsetReader` uses `RandomAccessFile` to seek directly to the exact byte offset for a candidate tweet and parse it efficiently.
    - **Branching**: Full relationship structures (e.g., Customer → AmazonHelp → Customer) are strictly preserved within the `HistoricalCandidate` model.
    - **Separation of Concerns**: Semantic re-ranking is explicitly excluded. This layer only produces valid, deterministic candidates for future pipelines to rank.
- **Phase 4**: Pending (AI #2 Semantic Relevance Ranking). No vector databases or embeddings have been implemented yet.
- **Phase 5**: Pending (AI #3 Grounded Response Generation).
- **Phase 6**: Pending (Escalation decision).
