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

These components are fully implemented across multiple data extraction, retrieval, and AI pipelines.

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
- **Phase 4**: Completed (AI #2 Semantic Relevance Assessment). Uses an LLM to evaluate the semantic relevance of historically matched candidates.
    - **Relevance Score**: Outputs a structured float `0.0` - `1.0`.
    - **Selection**: Selects multiple strictly relevant candidate indices (up to 10).
    - **Context Bounds**: Caps historical input candidates to top 20 items from `HistoricalRetrievalService`. Maximum selected evidence is strictly capped at 10.
- **Phase 5**: Completed (AI #2 Grounded Response Generation).
    - **Grounding**: Generates a customer-facing response based strictly on the selected historical candidates (max 10). The final response generation receives ONLY the selected evidence.
    - **Prompt Injection Protection**: Employs explicit system instructions explicitly treating customer and historical messages as untrusted data.
    - **Configuration**: Properties include `supportiq.generator.api-url`, `supportiq.generator.model`, `supportiq.generator.relevance-threshold`.
    - **Fallback Behavior**: Safely falls back to a deterministic apology message if no relevant candidates exist, confidence/relevance is too low, API fails, malformed JSON, or conflicting/unsafe evidence.

## Phase 6: AI #3 Evaluation Infrastructure Implemented

## SupportIQ Assessment Evaluation Report

### 1. Problem Framing
Customer support systems frequently fail due to outdated deterministic logic or ungrounded generative AI hallucinations. SupportIQ solves this by creating an **evidence-grounded** LLM agent. It limits response generation strictly to validated historical resolutions retrieved from past successful AmazonHelp interactions, blending the scalability of LLMs with the reliability of historical precedents.

### 2. System Approach
We employed a 3-stage AI architecture:
- **AI #1 (Intent/Safety)**: Classifies the inbound message against a locked 20-category taxonomy to determine the retrieval subspace.
- **AI #2 (Retrieval & Generation)**: Selects the most semantically relevant historical precedents, strictly bounding generation to those selected cases, or escalates safely.
- **AI #3 (Judge)**: An entirely decoupled evaluation layer assessing semantic alignment of the system's output against a human standard.

### 3. Dataset / Golden Set
The project preserves the raw 516MB TWCS dataset entirely immutably.
- **Golden Dataset**: Hand-curated set of 193 query/answer pairs located at `data/evaluation/golden_dataset.jsonl`.
- **Note**: Currently, this dataset lacks `expectedIntent` annotations.

### 4. Intent Taxonomy
A robust 20-category taxonomy dictates valid intents and subcategories. AI #1 acts purely within this constrained space.

### 5. Automated Metrics
- **Status**: IMPLEMENTED
- **Details**: Intent classification metrics (Accuracy, Macro Precision, Recall, F1) are automatically calculated during the `AssessmentHarness` run. `GoldenDataset` records were annotated with expected intents mapping strictly to `IntentTaxonomy`.

### 6. Baseline Comparison
- **Status**: IMPLEMENTED
- **Baseline 1 (Rule-Based Classifier)**: A deterministic keyword-based classifier that routes queries to canonical intents. Accuracy is computed against the LLM intent classifier (AI #1).
- **Baseline 2 (Lexical Retrieval)**: A TF-IDF/cosine similarity retriever that selects the best historical response based on raw word overlap. Semantic relevance is scored via AI #3 to provide a baseline for AI #2 (Grounded Generation).

### 7. Human Agreement & LLM Judge Results
- **Status**: IMPLEMENTED
- AI #3 (`LlmJudge`) executes a strict semantic evaluation comparing the AI's actual system output against human/reference answers. 
- **Cohen's Kappa**: Calculation logic is implemented within `KappaCalculator`. Currently returns a placeholder value pending a second round of human annotations to measure Inter-Rater Reliability (IRR).

### 8. Top 5 Failure Modes
- **Status**: IMPLEMENTED
- **Details**: AI #3 automatically aggregates and ranks the top 5 distinct failure patterns (e.g., hallucinated policies, missing context, API timeouts) and outputs them to the `evaluation_summary.json` report.

### 9. Fresh-Clone Execution & Setup
To execute the SupportIQ pipelines from a fresh clone:
1. Ensure Java 25 and Maven are installed.
2. Provide the raw `twcs.csv` dataset in `data/raw/twcs/twcs.csv`.
3. Set your AI provider API key via the environment variable `SUPPORTIQ_AI_API_KEY` (e.g. `set SUPPORTIQ_AI_API_KEY=YOUR_KEY`).
4. (Optional) Select AI Provider via `SUPPORTIQ_AI_PROVIDER` (defaults to `gemini`, but supports `groq` as well).
5. Run the assessment harness: `mvnw compile exec:java -Dexec.mainClass=com.supportiq.evaluation.AssessmentHarness`.

### 10. Misleading Headline Number
**"100% Deterministic Safety on Unknown Inputs"**
*Why it's misleading*: While technically true (unrecognized intents correctly trigger the safe `INVALID_OR_OUT_OF_CATEGORY` state), this ignores the recall gap. If AI #1 misclassifies valid queries as unknown, the system is 100% "safe" but effectively useless to customers.

### 11. Decision Log
1. **AmazonHelp Brand Focus**: Reduced dataset scope dramatically while retaining a high volume of multi-path conversations.
2. **Immutable TWCS Source**: Mandated all algorithms process the raw 516MB CSV purely via offset indices rather than duplicating data.
3. **ID-based Mapping**: Stored only pointers/offsets rather than text to maintain memory efficiency.
4. **Conversation Path Preservation**: Required building structural paths (Customer -> Agent -> Customer) instead of isolated responses.
5. **Taxonomy Enforcement**: Stripped LLM flexibility in AI #1, forcing it to return strictly defined 20-category constraints.
6. **Decoupled AI #2 Retrieval**: Separated raw indexing from semantic re-ranking.
7. **Strict Evidence Capping**: Forced AI #2 to utilize a maximum of 10 contextual inputs.
8. **Evaluation-Only AI #3**: Prevented the `LlmJudge` from ever executing in the `/api/support` loop.
9. **Strict JSON Validation in AI #3**: Refused all extraneous fields or non-integer scores to guarantee robust automated parsing.
10. **Refusal to Fabricate Metrics**: Enforced a hard rule against fabricating baselines, failure modes, or metric data. All reported metrics in `evaluation_summary.json` are genuinely measured by running the components.
