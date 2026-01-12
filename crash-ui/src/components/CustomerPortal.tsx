import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { messageAPI } from '../services/api';
import { EmailViewer } from './EmailViewer';
import type { Message, Customer } from '../types';
import './EmailClient.css';

interface CustomerPortalProps {
  customers: Customer[];
}

export const CustomerPortal: React.FC<CustomerPortalProps> = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);

  // Derive selected message from ID - this prevents re-render issues
  const selectedMessage = useMemo(() => {
    if (selectedId === null) return null;
    return messages.find(m => m.id === selectedId) || null;
  }, [selectedId, messages]);

  const fetchMessages = useCallback(async () => {
    try {
      const data = await messageAPI.getCustomerMessages();
      setMessages(data);
      // Clear selection for deleted messages
      setSelectedIds(prev => {
        const newSet = new Set<number>();
        prev.forEach(id => {
          if (data.some(msg => msg.id === id)) {
            newSet.add(id);
          }
        });
        return newSet;
      });
      // Clear selected message if it was deleted
      setSelectedId(prev => {
        if (prev !== null && !data.some(msg => msg.id === prev)) {
          return null;
        }
        return prev;
      });
    } catch (error) {
      console.error('Failed to fetch customer messages:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMessages();
    const interval = setInterval(fetchMessages, 5000);
    return () => clearInterval(interval);
  }, [fetchMessages]);

  const handleSelectAll = () => {
    if (selectedIds.size === messages.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(messages.map(m => m.id)));
    }
  };

  const handleToggleSelect = (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedIds(prev => {
      const newSet = new Set(prev);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      return newSet;
    });
  };

  const handleDeleteSelected = async () => {
    if (selectedIds.size === 0) return;
    if (!confirm(`Delete ${selectedIds.size} selected message(s)?`)) return;

    try {
      await messageAPI.deleteAllMessages();
      setMessages([]);
      setSelectedIds(new Set());
      setSelectedId(null);
    } catch (error) {
      console.error('Failed to delete messages:', error);
    }
  };

  if (loading) {
    return <div className="email-client"><div className="loading">Loading...</div></div>;
  }

  return (
    <div className="email-client">
      <div className="email-list-panel">
        <div className="email-toolbar">
          <label className="select-all-container">
            <input
              type="checkbox"
              checked={messages.length > 0 && selectedIds.size === messages.length}
              onChange={handleSelectAll}
              disabled={messages.length === 0}
            />
            <span>Select All</span>
          </label>
          <button
            className="toolbar-btn delete"
            onClick={handleDeleteSelected}
            disabled={selectedIds.size === 0}
          >
            Delete ({selectedIds.size})
          </button>
          <span className="message-count">{messages.length} message(s)</span>
        </div>

        <div className="email-list">
          {messages.length === 0 ? (
            <div className="empty-state">
              <p>No customer emails yet</p>
              <p className="hint">Trigger an accident to see emails appear here</p>
            </div>
          ) : (
            messages.map((msg) => (
              <div
                key={msg.id}
                className={`email-row ${selectedId === msg.id ? 'active' : ''} ${selectedIds.has(msg.id) ? 'checked' : ''}`}
                onClick={() => setSelectedId(msg.id)}
              >
                <input
                  type="checkbox"
                  checked={selectedIds.has(msg.id)}
                  onClick={(e) => handleToggleSelect(msg.id, e)}
                  onChange={() => {}}
                />
                <div className="email-row-content">
                  <div className="email-subject">{msg.customerName || 'Customer'}</div>
                  <div className="email-meta">
                    <span className="claim-ref">{msg.claimReference}</span>
                    <span className="email-date">{new Date(msg.sentAt).toLocaleString()}</span>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      <div className="email-detail-panel">
        {selectedMessage ? (
          <>
            <div className="email-detail-header">
              <h2>{selectedMessage.subject}</h2>
              <div className="email-detail-meta">
                <div><strong>Claim:</strong> {selectedMessage.claimReference}</div>
                <div><strong>Sent:</strong> {new Date(selectedMessage.sentAt).toLocaleString()}</div>
              </div>
            </div>
            <div className="email-detail-body">
              <EmailViewer html={selectedMessage.body} />
            </div>
          </>
        ) : (
          <div className="no-selection">
            <p>Select an email to view its contents</p>
          </div>
        )}
      </div>
    </div>
  );
};
