# CRASH UI Implementation Plan

## Goal
Create a modern, tabbed web interface for the `imc-crash` application with three main views:
1.  **CRASH Architecture**: Live visualization of the system state using WebSockets.
2.  **Agent Portal**: Simulated email client for adjusters and customer lookup tool.
3.  **Customer Portal**: Simulated email and SMS clients for customers with dynamic filtering.

## Changes Overview

### 1. crash-orchestrator (Backend)
Enable real-time status updates for the visualization.

#### [NEW] [AccidentStatusService.java](file:///Users/dbbaskette/Projects/insurance-megacorp/imc-crash/crash-orchestrator/src/main/java/com/insurancemegacorp/crash/orchestrator/AccidentStatusService.java)
- Service handles WebSocket template messaging.
- Methods: `broadcastStatus(String agent, String status, String statusType)`.
- Method: `broadcastCustomer(int policyId, String driverName, String contactInfo)`.

#### [NEW] [WebSocketConfig.java](file:///Users/dbbaskette/Projects/insurance-megacorp/imc-crash/crash-orchestrator/src/main/java/com/insurancemegacorp/crash/orchestrator/config/WebSocketConfig.java)
- Configure Spring WebSocket / STOMP.
- Endpoint: `/ws-crash`.
- Destination prefix: `/topic`.

#### [MODIFY] [CrashAgent.java](file:///Users/dbbaskette/Projects/insurance-megacorp/imc-crash/crash-orchestrator/src/main/java/com/insurancemegacorp/crash/orchestrator/CrashAgent.java)
- Inject `AccidentStatusService`.
- Add `broadcastStatus` calls at the start and end of each `@Action` method.
- Add `broadcastCustomer` call after `lookupPolicy` action.

### 2. crash-mcp-communications (Backend)
Enable simulation and retrieval of emails/SMS.

#### [NEW] [SimulationMessageStore.java](file:///Users/dbbaskette/Projects/insurance-megacorp/imc-crash/crash-mcp-communications/src/main/java/com/insurancemegacorp/crash/communications/SimulationMessageStore.java)
- Component to hold in-memory list of `SimulationMessage` records.
- Fields: `id`, `recipient`, `type` (EMAIL/SMS/PUSH), `subject`, `body`, `timestamp`, `direction`.

#### [NEW] [SimulationController.java](file:///Users/dbbaskette/Projects/insurance-megacorp/imc-crash/crash-mcp-communications/src/main/java/com/insurancemegacorp/crash/communications/SimulationController.java)
- REST API for the UI.
- `GET /api/simulation/messages?recipient={email/phone}`.
- `GET /api/simulation/messages/agent` (for adjuster emails).

#### [MODIFY] [CommunicationsService.java](file:///Users/dbbaskette/Projects/insurance-megacorp/imc-crash/crash-mcp-communications/src/main/java/com/insurancemegacorp/crash/communications/CommunicationsService.java)
- Inject `SimulationMessageStore`.
- In `sendSms`, `sendFnolEmail`, etc., save the full message content to the store.

### 3. crash-mcp-policy (Backend)
Enable customer search and retrieval.

#### [MODIFY] [DriverRepository.java](file:///Users/dbbaskette/Projects/insurance-megacorp/imc-crash/crash-mcp-policy/src/main/java/com/insurancemegacorp/crash/policy/repository/DriverRepository.java)
- Add `findByFullNameContainingIgnoreCase(String name)`.

#### [MODIFY] [PolicyService.java](file:///Users/dbbaskette/Projects/insurance-megacorp/imc-crash/crash-mcp-policy/src/main/java/com/insurancemegacorp/crash/policy/PolicyService.java)
- Add `searchDrivers(String query)` method.

#### [NEW] [PolicyController.java](file:///Users/dbbaskette/Projects/insurance-megacorp/imc-crash/crash-mcp-policy/src/main/java/com/insurancemegacorp/crash/policy/PolicyController.java)
- `GET /api/policy/search?q={query}`.

### 4. crash-ui (Frontend)
New Vite + React application.

#### Structure
- `src/components/`
  - `ArchitectureView.tsx`: Interactive SVG/Canvas diagram. Subscribes to `/topic/status`.
  - `SimulatedEmailClient.tsx`: Master-detail view for emails.
  - `SimulatedSmsClient.tsx`: Chat bubble interface.
  - `CustomerSearch.tsx`: Search bar and result cards.
  - `CustomerSidebar.tsx`: Lists active/detected customers (populated via `CUSTOMER_DETECTED` event).
- `src/pages/`
  - `Dashboard.tsx`: Hosts ArchitectureView.
  - `AgentPortal.tsx`: Hosts EmailClient (Adjuster mode) + CustomerSearch.
  - `CustomerPortal.tsx`: Master view with CustomerSidebar and Email/SMS clients.

## Verification Plan

### Manual Verification
1.  **Start all services** (Orchestrator, Agents, and new UI).
2.  **Open browser** to `http://localhost:5173`.
3.  **Run Simulation** (e.g., `curl` command or UI triggers).
4.  **Verify Architecture Tab**:
    - Observe diagram nodes changing color.
5.  **Verify Agent Tab**:
    - Check for "FNOL Report" email.
    - Search for "John Doe" (or demo driver).
6.  **Verify Customer Tab**:
    - Observe "John Doe" appearing in the sidebar automatically via WebSocket.
    - Click "John Doe" and verify SMS/Email history is filtered correctly.
