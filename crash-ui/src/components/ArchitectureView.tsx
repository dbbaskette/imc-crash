import { useEffect, useState } from 'react';
import type { AgentStatusMessage } from '../types';

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

interface NodeDef {
  actionKey: string;
  name: string;
  subtitle?: string;
  x: number;
  y: number;
}

const NODES: Record<string, NodeDef> = {
  accidentEvent: { actionKey: 'accident-event', name: 'AccidentEvent', subtitle: '(from telemetry)', x: 400, y: 50 },
  gatherEnvironment: { actionKey: 'gatherEnvironment', name: 'gatherEnvironment', x: 150, y: 170 },
  analyzeImpact: { actionKey: 'analyzeImpact', name: 'analyzeImpact', x: 400, y: 170 },
  lookupPolicy: { actionKey: 'lookupPolicy', name: 'lookupPolicy', x: 650, y: 170 },
  findServices: { actionKey: 'findServices', name: 'findServices', x: 280, y: 310 },
  initiateComms: { actionKey: 'initiateComms', name: 'initiateComms', x: 520, y: 310 },
  compileReport: { actionKey: 'compileReport', name: 'compileReport', x: 400, y: 450 },
  sendFnolToAdjuster: { actionKey: 'sendFnolToAdjuster', name: 'sendFnolToAdjuster', x: 400, y: 550 },
  emailSent: { actionKey: 'goal-achieved', name: 'Email sent + DB persisted', x: 400, y: 640 },
};

const CONNECTIONS = [
  { from: 'accidentEvent', to: 'gatherEnvironment' },
  { from: 'accidentEvent', to: 'analyzeImpact' },
  { from: 'accidentEvent', to: 'lookupPolicy' },
  { from: 'analyzeImpact', to: 'findServices' },
  { from: 'analyzeImpact', to: 'initiateComms' },
  { from: 'lookupPolicy', to: 'initiateComms' },
  { from: 'gatherEnvironment', to: 'compileReport' },
  { from: 'findServices', to: 'compileReport' },
  { from: 'initiateComms', to: 'compileReport' },
  { from: 'compileReport', to: 'sendFnolToAdjuster' },
  { from: 'sendFnolToAdjuster', to: 'emailSent' },
];

const LEVELS = [
  { y: 170, label: 'Level 0 - Parallel (Impact, Env, Policy)', width: 620 },
  { y: 310, label: 'Level 1 - Parallel (Services, Comms)', width: 360 },
  { y: 450, label: 'Level 2 - Compile Report', width: 180 },
];

export function ArchitectureView({ agentStatus }: ArchitectureViewProps) {
  const [actionStates, setActionStates] = useState<Record<string, ActionState>>({});
  const [goalAchieved, setGoalAchieved] = useState(false);

  useEffect(() => {
    if (agentStatus) {
      const { action, status } = agentStatus;
      console.log('Received agent status:', agentStatus);

      setActionStates((prev) => ({
        ...prev,
        [action]: {
          status: mapWebSocketStatusToActionStatus(status),
        },
      }));

      if (action === 'sendFnolToAdjuster' && status === 'COMPLETED') {
        setGoalAchieved(true);
        setTimeout(() => setGoalAchieved(false), 10000);
      }

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

  function getNodeFill(type: 'input' | 'action' | 'goal', status: ActionStatus): string {
    if (type === 'input') return 'url(#inputGradient)';
    if (type === 'goal') return goalAchieved ? 'url(#goalGradient)' : 'url(#idleGradient)';

    switch (status) {
      case 'active':
        return 'url(#activeGradient)';
      case 'completed':
        return 'url(#completedGradient)';
      default:
        return 'url(#idleGradient)';
    }
  }

  function getNodeStroke(type: 'input' | 'action' | 'goal', status: ActionStatus): string {
    if (type === 'input') return '#6366f1';
    if (type === 'goal') return goalAchieved ? '#10b981' : '#475569';

    switch (status) {
      case 'active':
        return '#3b82f6';
      case 'completed':
        return '#10b981';
      default:
        return '#475569';
    }
  }

  function getGlowFilter(type: 'input' | 'action' | 'goal', status: ActionStatus): string {
    if (type === 'goal' && goalAchieved) return 'url(#goalGlow)';
    if (status === 'active') return 'url(#activeGlow)';
    if (status === 'completed') return 'url(#completedGlow)';
    return '';
  }

  function renderNode(key: string, node: NodeDef, type: 'input' | 'action' | 'goal') {
    const status = getActionStatus(node.actionKey);
    const width = type === 'goal' ? 200 : 150;
    const height = type === 'input' && node.subtitle ? 55 : 45;

    return (
      <g key={key}>
        <rect
          x={node.x - width / 2}
          y={node.y - height / 2}
          width={width}
          height={height}
          rx={type === 'goal' ? 12 : 8}
          fill={getNodeFill(type, status)}
          stroke={getNodeStroke(type, status)}
          strokeWidth={2}
          filter={getGlowFilter(type, status)}
          className={status === 'active' ? 'animate-pulse' : ''}
        />
        <text
          x={node.x}
          y={node.subtitle ? node.y - 4 : node.y + 4}
          textAnchor="middle"
          fill="#fff"
          fontSize="12"
          fontFamily="'Inter', system-ui, sans-serif"
          fontWeight="500"
        >
          {node.name}
        </text>
        {node.subtitle && (
          <text
            x={node.x}
            y={node.y + 14}
            textAnchor="middle"
            fill="#94a3b8"
            fontSize="10"
            fontFamily="'Inter', system-ui, sans-serif"
          >
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

    const fromY = fromNode.y + 22;
    const toY = toNode.y - 22;
    const midY = (fromY + toY) / 2;

    return (
      <path
        key={index}
        d={`M ${fromNode.x} ${fromY} C ${fromNode.x} ${midY}, ${toNode.x} ${midY}, ${toNode.x} ${toY}`}
        fill="none"
        stroke="url(#connectionGradient)"
        strokeWidth="2"
        strokeLinecap="round"
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
          y={level.y - 55}
          width={level.width}
          height={110}
          rx={12}
          fill="url(#levelGradient)"
          stroke="#334155"
          strokeWidth="1"
        />
        <text
          x={400}
          y={level.y - 65}
          textAnchor="middle"
          fill="#64748b"
          fontSize="11"
          fontFamily="'Inter', system-ui, sans-serif"
          fontWeight="500"
        >
          {level.label}
        </text>
      </g>
    );
  }

  return (
    <div className="p-8 flex flex-col items-center">
      <h2 className="text-2xl font-bold text-white mb-6 flex items-center gap-3">
        <span className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-sm">
          ⚡
        </span>
        GOAP Execution Flow
      </h2>

      <svg width="800" height="720" className="rounded-xl overflow-hidden" style={{ background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)' }}>
        <defs>
          {/* Gradients for nodes */}
          <linearGradient id="idleGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#334155" />
            <stop offset="100%" stopColor="#1e293b" />
          </linearGradient>

          <linearGradient id="inputGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#4f46e5" />
            <stop offset="100%" stopColor="#6366f1" />
          </linearGradient>

          <linearGradient id="activeGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#2563eb" />
            <stop offset="100%" stopColor="#3b82f6" />
          </linearGradient>

          <linearGradient id="completedGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#059669" />
            <stop offset="100%" stopColor="#10b981" />
          </linearGradient>

          <linearGradient id="goalGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#047857" />
            <stop offset="100%" stopColor="#10b981" />
          </linearGradient>

          <linearGradient id="levelGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="rgba(51, 65, 85, 0.3)" />
            <stop offset="100%" stopColor="rgba(30, 41, 59, 0.3)" />
          </linearGradient>

          <linearGradient id="connectionGradient" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#475569" />
            <stop offset="100%" stopColor="#64748b" />
          </linearGradient>

          {/* Glow filters */}
          <filter id="activeGlow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feFlood floodColor="#3b82f6" floodOpacity="0.5" />
            <feComposite in2="blur" operator="in" />
            <feMerge>
              <feMergeNode />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>

          <filter id="completedGlow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feFlood floodColor="#10b981" floodOpacity="0.4" />
            <feComposite in2="blur" operator="in" />
            <feMerge>
              <feMergeNode />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>

          <filter id="goalGlow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="6" result="blur" />
            <feFlood floodColor="#10b981" floodOpacity="0.6" />
            <feComposite in2="blur" operator="in" />
            <feMerge>
              <feMergeNode />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>

          {/* Arrow marker */}
          <marker
            id="arrowhead"
            markerWidth="10"
            markerHeight="7"
            refX="9"
            refY="3.5"
            orient="auto"
          >
            <polygon points="0 0, 10 3.5, 0 7" fill="#64748b" />
          </marker>
        </defs>

        {/* Level backgrounds */}
        {LEVELS.map((level, i) => renderLevelBackground(level, i))}

        {/* Connections */}
        {CONNECTIONS.map((conn, i) => renderConnection(conn, i))}

        {/* Nodes */}
        {renderNode('accidentEvent', NODES.accidentEvent, 'input')}
        {renderNode('gatherEnvironment', NODES.gatherEnvironment, 'action')}
        {renderNode('analyzeImpact', NODES.analyzeImpact, 'action')}
        {renderNode('lookupPolicy', NODES.lookupPolicy, 'action')}
        {renderNode('findServices', NODES.findServices, 'action')}
        {renderNode('initiateComms', NODES.initiateComms, 'action')}
        {renderNode('compileReport', NODES.compileReport, 'action')}
        {renderNode('sendFnolToAdjuster', NODES.sendFnolToAdjuster, 'action')}
        {renderNode('emailSent', NODES.emailSent, 'goal')}
      </svg>

      {/* Legend */}
      <div className="flex gap-6 mt-6">
        {[
          { label: 'Idle', color: 'bg-slate-600' },
          { label: 'Active', color: 'bg-blue-500', glow: true },
          { label: 'Completed', color: 'bg-emerald-500' },
          { label: 'Goal Achieved', color: 'bg-emerald-400', ring: true },
        ].map((item) => (
          <div key={item.label} className="flex items-center gap-2">
            <div className={`w-4 h-4 rounded ${item.color} ${item.glow ? 'animate-pulse shadow-lg shadow-blue-500/50' : ''} ${item.ring ? 'ring-2 ring-emerald-400/50' : ''}`} />
            <span className="text-sm text-slate-400">{item.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
