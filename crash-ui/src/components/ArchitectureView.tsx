import { useEffect, useState } from 'react';
import type { AgentStatusMessage } from '../types';
import './ArchitectureView.css';

type ActionStatus = 'idle' | 'active' | 'completed';

interface ActionState {
  status: ActionStatus;
}

function mapWebSocketStatusToActionStatus(wsStatus: string): ActionStatus {
  switch (wsStatus) {
    case 'STARTED':
      return 'active';
    case 'COMPLETED':
      return 'completed';
    default:
      return 'idle';
  }
}

interface ArchitectureViewProps {
  agentStatus: AgentStatusMessage | null;
}

// Node definitions matching the actual GOAP execution flow
// The 'actionKey' matches the action name sent in WebSocket broadcasts
interface NodeDef {
  actionKey: string; // matches the 'action' field from WebSocket
  name: string;
  subtitle?: string;
  x: number;
  y: number;
}

const NODES: Record<string, NodeDef> = {
  // Input
  accidentEvent: { actionKey: 'accident-event', name: 'AccidentEvent', subtitle: '(from telemetry)', x: 400, y: 50 },
  // Level 0 - Parallel Execution
  gatherEnvironment: { actionKey: 'gatherEnvironment', name: 'gatherEnvironment', x: 150, y: 170 },
  analyzeImpact: { actionKey: 'analyzeImpact', name: 'analyzeImpact', x: 400, y: 170 },
  lookupPolicy: { actionKey: 'lookupPolicy', name: 'lookupPolicy', x: 650, y: 170 },
  // Level 1 - Parallel Execution (services + comms run concurrently)
  findServices: { actionKey: 'findServices', name: 'findServices', x: 280, y: 310 },
  initiateComms: { actionKey: 'initiateComms', name: 'initiateComms', x: 520, y: 310 },
  // Level 2 - Aggregation
  compileReport: { actionKey: 'compileReport', name: 'compileReport', x: 400, y: 450 },
  // Level 3 - Goal Achievement (sends emails)
  sendFnolToAdjuster: { actionKey: 'sendFnolToAdjuster', name: 'sendFnolToAdjuster', x: 400, y: 550 },
  // Final
  emailSent: { actionKey: 'goal-achieved', name: 'Email sent + DB persisted', x: 400, y: 640 },
};

// Connections between nodes
const CONNECTIONS = [
  // From AccidentEvent to Level 0 (parallel)
  { from: 'accidentEvent', to: 'gatherEnvironment' },
  { from: 'accidentEvent', to: 'analyzeImpact' },
  { from: 'accidentEvent', to: 'lookupPolicy' },
  // From Level 0 to Level 1 (parallel)
  { from: 'analyzeImpact', to: 'findServices' },
  { from: 'analyzeImpact', to: 'initiateComms' },
  { from: 'lookupPolicy', to: 'initiateComms' },
  // All feed into compileReport
  { from: 'gatherEnvironment', to: 'compileReport' },
  { from: 'findServices', to: 'compileReport' },
  { from: 'initiateComms', to: 'compileReport' },
  // compileReport goes to sendFnolToAdjuster
  { from: 'compileReport', to: 'sendFnolToAdjuster' },
  // sendFnolToAdjuster completes the flow
  { from: 'sendFnolToAdjuster', to: 'emailSent' },
];

// Level labels - Both Level 0 and Level 1 execute in parallel using CompletableFuture
const LEVELS = [
  { y: 170, label: 'Level 0 - Parallel (Impact, Env, Policy)', width: 620 },
  { y: 310, label: 'Level 1 - Parallel (Services, Comms)', width: 360 },
  { y: 450, label: 'Level 2 - Compile Report', width: 180 },
];

export function ArchitectureView({ agentStatus }: ArchitectureViewProps) {
  // Track status by action name (e.g., "analyzeImpact", "gatherEnvironment")
  const [actionStates, setActionStates] = useState<Record<string, ActionState>>({});
  const [goalAchieved, setGoalAchieved] = useState(false);

  useEffect(() => {
    if (agentStatus) {
      const { action, status } = agentStatus;

      console.log('Received agent status:', agentStatus);

      // Update the action state
      setActionStates((prev) => ({
        ...prev,
        [action]: {
          status: mapWebSocketStatusToActionStatus(status),
        },
      }));

      // Check if this is the final action completing
      if (action === 'sendFnolToAdjuster' && status === 'COMPLETED') {
        setGoalAchieved(true);
        // Reset goal after a while
        setTimeout(() => setGoalAchieved(false), 10000);
      }

      // Auto-fade back to idle after completion (keep visible longer)
      if (status === 'COMPLETED') {
        setTimeout(() => {
          setActionStates((prev) => ({
            ...prev,
            [action]: { status: 'idle' },
          }));
        }, 5000);
      }
    }
  }, [agentStatus]);

  function getActionStatus(actionKey: string): ActionStatus {
    return actionStates[actionKey]?.status ?? 'idle';
  }

  function getNodeClassName(type: 'input' | 'action' | 'output' | 'goal', status: ActionStatus): string {
    const base = 'goap-node';
    switch (type) {
      case 'input':
        return `${base} input-node`;
      case 'goal':
        return goalAchieved ? `${base} goal-node achieved` : `${base} goal-node`;
      default:
        return `${base} action-node ${status}`;
    }
  }

  function renderNode(key: string, node: NodeDef, type: 'input' | 'action' | 'output' | 'goal') {
    const status = getActionStatus(node.actionKey);
    const width = type === 'goal' ? 180 : 140;
    const height = type === 'input' && node.subtitle ? 50 : 40;
    const className = getNodeClassName(type, status);

    return (
      <g key={key}>
        <rect
          x={node.x - width / 2}
          y={node.y - height / 2}
          width={width}
          height={height}
          rx={type === 'goal' ? 8 : 4}
          className={className}
        />
        <text x={node.x} y={node.subtitle ? node.y - 2 : node.y + 4} textAnchor="middle" className="node-text">
          {node.name}
        </text>
        {node.subtitle && (
          <text x={node.x} y={node.y + 14} textAnchor="middle" className="node-subtitle">
            {node.subtitle}
          </text>
        )}
      </g>
    );
  }

  function renderConnection(conn: { from: string; to: string }, index: number) {
    const fromNode = NODES[conn.from];
    const toNode = NODES[conn.to];
    if (!fromNode || !toNode) return null;

    const fromY = fromNode.y + 20;
    const toY = toNode.y - 20;
    const midY = (fromY + toY) / 2;

    return (
      <path
        key={index}
        d={`M ${fromNode.x} ${fromY} C ${fromNode.x} ${midY}, ${toNode.x} ${midY}, ${toNode.x} ${toY}`}
        className="connection-line"
        markerEnd="url(#arrowhead)"
      />
    );
  }

  function renderLevelBackground(level: { y: number; label: string; width: number }, index: number) {
    const x = 400 - level.width / 2;

    return (
      <g key={index}>
        <rect
          x={x}
          y={level.y - 50}
          width={level.width}
          height={100}
          rx={8}
          className="level-background"
        />
        <text x={400} y={level.y - 58} textAnchor="middle" className="level-label">
          {level.label}
        </text>
      </g>
    );
  }

  return (
    <div className="architecture-view">
      <h2>GOAP Execution Flow</h2>
      <svg width="800" height="700" className="goap-diagram">
        {/* Arrow marker definition */}
        <defs>
          <marker
            id="arrowhead"
            markerWidth="10"
            markerHeight="7"
            refX="9"
            refY="3.5"
            orient="auto"
          >
            <polygon points="0 0, 10 3.5, 0 7" fill="#888" />
          </marker>
        </defs>

        {/* Level backgrounds */}
        {LEVELS.map((level, i) => renderLevelBackground(level, i))}

        {/* Connections */}
        {CONNECTIONS.map((conn, i) => renderConnection(conn, i))}

        {/* Input node */}
        {renderNode('accidentEvent', NODES.accidentEvent, 'input')}

        {/* Level 0 - Parallel Execution */}
        {renderNode('gatherEnvironment', NODES.gatherEnvironment, 'action')}
        {renderNode('analyzeImpact', NODES.analyzeImpact, 'action')}
        {renderNode('lookupPolicy', NODES.lookupPolicy, 'action')}

        {/* Level 1 - Severity-Dependent */}
        {renderNode('findServices', NODES.findServices, 'action')}
        {renderNode('initiateComms', NODES.initiateComms, 'action')}

        {/* Level 2 - Compile Report */}
        {renderNode('compileReport', NODES.compileReport, 'action')}

        {/* Level 3 - Send Emails */}
        {renderNode('sendFnolToAdjuster', NODES.sendFnolToAdjuster, 'action')}

        {/* Goal achieved */}
        {renderNode('emailSent', NODES.emailSent, 'goal')}
      </svg>

      <div className="legend">
        <div className="legend-item">
          <div className="legend-color idle"></div>
          <span>Idle</span>
        </div>
        <div className="legend-item">
          <div className="legend-color active"></div>
          <span>Active</span>
        </div>
        <div className="legend-item">
          <div className="legend-color completed"></div>
          <span>Completed</span>
        </div>
        <div className="legend-item">
          <div className="legend-color goal"></div>
          <span>Goal Achieved</span>
        </div>
      </div>
    </div>
  );
};
