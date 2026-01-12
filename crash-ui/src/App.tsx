import { useState, useEffect } from 'react';
import { websocketService } from './services/websocket';
import { ArchitectureView } from './components/ArchitectureView';
import { AgentPortal } from './components/AgentPortal';
import { CustomerPortal } from './components/CustomerPortal';
import type { AgentStatusMessage, Customer } from './types';
import './App.css';

type Tab = 'architecture' | 'agent' | 'customer';

function App() {
  // Persist tab state in localStorage
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
          // Avoid duplicates
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
    <div className="App">
      <header className="app-header">
        <h1>🚗 CRASH - Claims Response Agent System Hive</h1>
        <div className="connection-status">
          <span className={`status-dot ${connected ? 'connected' : 'disconnected'}`}></span>
          {connected ? 'Connected' : 'Disconnected'}
        </div>
      </header>

      <nav className="tabs">
        <button
          className={`tab ${activeTab === 'architecture' ? 'active' : ''}`}
          onClick={() => handleTabChange('architecture')}
        >
          Architecture View
        </button>
        <button
          className={`tab ${activeTab === 'agent' ? 'active' : ''}`}
          onClick={() => handleTabChange('agent')}
        >
          Agent Portal
        </button>
        <button
          className={`tab ${activeTab === 'customer' ? 'active' : ''}`}
          onClick={() => handleTabChange('customer')}
        >
          Customer Portal
        </button>
      </nav>

      <main className="content">
        {activeTab === 'architecture' && <ArchitectureView agentStatus={agentStatus} />}
        {activeTab === 'agent' && <AgentPortal />}
        {activeTab === 'customer' && <CustomerPortal customers={customers} />}
      </main>
    </div>
  );
}

export default App;
