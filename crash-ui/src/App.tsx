import { useState, useEffect } from 'react';
import { websocketService } from './services/websocket';
import { ArchitectureView } from './components/ArchitectureView';
import { AgentPortal } from './components/AgentPortal';
import { CustomerPortal } from './components/CustomerPortal';
import type { AgentStatusMessage, Customer } from './types';

type Tab = 'architecture' | 'agent' | 'customer';

const tabs: { id: Tab; label: string; icon: string }[] = [
  { id: 'architecture', label: 'Architecture', icon: '⚡' },
  { id: 'agent', label: 'Agent Portal', icon: '📋' },
  { id: 'customer', label: 'Customer Portal', icon: '👤' },
];

function App() {
  const [activeTab, setActiveTab] = useState<Tab>(() => {
    const saved = localStorage.getItem('crash-ui-tab');
    return (saved as Tab) || 'architecture';
  });
  const [agentStatus, setAgentStatus] = useState<AgentStatusMessage | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [connected, setConnected] = useState(false);

  function handleTabChange(tab: Tab) {
    setActiveTab(tab);
    localStorage.setItem('crash-ui-tab', tab);
  }

  useEffect(() => {
    websocketService.connect(
      (message) => setAgentStatus(message),
      (message) => {
        const customer: Customer = {
          name: message.customerName,
          claimReference: message.claimReference,
          phone: message.phone,
          email: message.email,
          timestamp: message.timestamp,
        };
        setCustomers((prev) => {
          if (prev.some((c) => c.claimReference === customer.claimReference)) {
            return prev;
          }
          return [customer, ...prev];
        });
      },
      () => setConnected(true),
      (error) => {
        console.error('WebSocket error:', error);
        setConnected(false);
      }
    );

    return () => {
      websocketService.disconnect();
    };
  }, []);

  return (
    <div className="h-screen flex flex-col bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 overflow-hidden">
      {/* Header */}
      <header className="bg-slate-800/50 backdrop-blur-sm border-b border-slate-700/50 sticky top-0 z-50">
        <div className="max-w-[1920px] mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            {/* Logo and Title */}
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center shadow-lg shadow-blue-500/20">
                <span className="text-xl">🚗</span>
              </div>
              <div>
                <h1 className="text-xl font-bold text-white tracking-tight">CRASH</h1>
                <p className="text-xs text-slate-400">Claims Response Agent System Hive</p>
              </div>
            </div>

            {/* Connection Status */}
            <div className="flex items-center gap-3">
              <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-medium transition-all duration-300 ${
                connected
                  ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                  : 'bg-red-500/10 text-red-400 border border-red-500/20'
              }`}>
                <span className={`w-2 h-2 rounded-full ${
                  connected
                    ? 'bg-emerald-400 shadow-lg shadow-emerald-500/50 animate-pulse'
                    : 'bg-red-400'
                }`} />
                {connected ? 'Connected' : 'Disconnected'}
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Navigation Tabs */}
      <nav className="bg-slate-800/30 border-b border-slate-700/30">
        <div className="max-w-[1920px] mx-auto px-6">
          <div className="flex gap-1">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => handleTabChange(tab.id)}
                className={`relative px-5 py-3 text-sm font-medium transition-all duration-200 ${
                  activeTab === tab.id
                    ? 'text-white'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <span className="flex items-center gap-2">
                  <span>{tab.icon}</span>
                  {tab.label}
                </span>
                {activeTab === tab.id && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-blue-500 to-purple-500 rounded-full" />
                )}
              </button>
            ))}
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main className="flex-1 flex flex-col min-h-0 p-4">
        <div className="bg-slate-800/30 backdrop-blur-sm rounded-2xl border border-slate-700/30 shadow-xl flex-1 flex flex-col min-h-0 overflow-hidden">
          {activeTab === 'architecture' && <ArchitectureView agentStatus={agentStatus} />}
          {activeTab === 'agent' && <AgentPortal />}
          {activeTab === 'customer' && <CustomerPortal customers={customers} />}
        </div>
      </main>
    </div>
  );
}

export default App;
