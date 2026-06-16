# SCHOLAR: An Intelligent AI-Powered Academic Orchestration Engine

SCHOLAR is a high-performance, local-first Android study workspace engineered to bridge the gap between static academic documentation and interactive, contextual learning. Built entirely using **Kotlin** and **Jetpack Compose**, the platform integrates an optimized native document rendering pipeline with a split-screen AI tutoring companion powered directly by the **Gemini Multimodal SDK**.

---

## Core Features

### 1. Dynamic Split-Screen Layout & Gestural Workspace
* **Fluid Space Allocation:** Built using reactive layout constraints, allowing users to drag an interactive divider bar to expand or contract space between the document viewer and the chat console dynamically based on real-time usage.
* **Intelligent IME Inset Handling:** Native integration with Android's Window Insets layer (`imePadding`), forcing the input matrix to scale fluidly above the soft keyboard without obstructing active text visibility.

### 2. Multi-Tab Native PDF Renderer
* **Zero-Latency Document Parsing:** Bypasses cloud pipeline storage bottlenecks by utilizing Android's low-level `PdfRenderer` on an IO dispatcher thread to compile documents directly into a scrollable, memory-mapped bitmap vector canvas.
* **Multi-Document Concurrency:** Supports managing multiple open syllabi or textbook assets concurrently via a top-tier contextual tab-row layout for seamless switching.

### 3. Context-Aware AI Grounding & Interactive Loop
* **Fallback Cognitive Modeling:** Powered by custom system instructions that prioritize the user's local notes and loaded documentation while dynamically scaling back to general baseline academic logic when open-ended science or technical concepts are requested.
* **Contextual Triggers:** Eliminates manual text input repetition by allowing direct page hooks to drop active layout telemetry straight into the chat input field.

---

## Architecture Stack

* **Language:** 100% Type-safe Kotlin
* **UI Framework:** Jetpack Compose (Declarative UI Components)
* **Asynchronous Orchestration:** Kotlin Coroutines & Asynchronous Flows
* **Core Intelligence:** Google Gemini Pro Vision API (Direct Mobile SDK)
* **Database & Persistence:** Cloud Firestore (Serverless Rule-vetted Document Ingestion)

---

## Local Compilation Guide

To run the SCHOLAR prototype locally on your development machine, ensure you have **Android Studio Ladybug (or newer)** and **JDK 21** configured:

1. Clone the repository:
   ```bash
   git clone [https://github.com/ghostminator/SCHOLAR.git](https://github.com/ghostminator/SCHOLAR.git)
