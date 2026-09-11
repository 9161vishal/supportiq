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
- **Phase 2B Step 3.1**: Completed (AI Intent Classification). A lightweight, zero-dependency LLM classifier (`LlmIntentClassifier`) built on `java.net.http.HttpClient` maps customer intent directly to `IntentTaxonomy`. 
    - **Configuration**: Properties include `supportiq.classifier.api-url`, `supportiq.classifier.api-key`, `supportiq.classifier.model` (default `gpt-4o-mini`), and `supportiq.classifier.confidence-threshold` (default `0.6`).
    - **Confidence**: Model-provided score (0.0 - 1.0); not a calibrated statistical probability.
    - **Uncertainty & Fallbacks**: Handles ambiguous/multilingual queries naturally. Low confidence triggers an `uncertain=true` flag. Invalid LLM JSON safely falls back to `GENERAL_INFORMATION_AND_NON_SUPPORT`.
    - **Tests**: Unit tests do not require a live LLM API.
- **Phase 2B Step 3.2**: Pending (Historical Retrieval & Embeddings). No vector databases, semantic re-ranking, or reply generation have been implemented yet.
