import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { messageAPI } from '../services/api';
import { EmailViewer } from './EmailViewer';
import type { Message, Customer } from '../types';

interface CustomerPortalProps {
  customers: Customer[];
}

export const CustomerPortal: React.FC<CustomerPortalProps> = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);

  const selectedMessage = useMemo(() => {
    if (selectedId === null) return null;
    return messages.find(m => m.id === selectedId) || null;
  }, [selectedId, messages]);

  const fetchMessages = useCallback(async () => {
    try {
      const data = await messageAPI.getCustomerMessages();
      setMessages(data);
      setSelectedIds(prev => {
        const newSet = new Set<number>();
        prev.forEach(id => {
          if (data.some(msg => msg.id === id)) {
            newSet.add(id);
          }
        });
        return newSet;
      });
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
    if (!confirm(`Delete ${selectedIds.size} selected customer message(s)?`)) return;

    try {
      // Delete only customer messages
      await messageAPI.deleteCustomerMessages();
      setMessages([]);
      setSelectedIds(new Set());
      setSelectedId(null);
    } catch (error) {
      console.error('Failed to delete customer messages:', error);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full min-h-[400px]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-3 border-purple-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-slate-400 text-sm">Loading messages...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-[280px_1fr] h-full min-h-0 overflow-hidden">
      {/* Left Panel - Email List */}
      <div className="flex flex-col border-r border-slate-700/50 bg-slate-800/20">
        {/* Toolbar */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-slate-700/50 bg-slate-800/30">
          <label className="flex items-center gap-2 cursor-pointer text-sm text-slate-300 hover:text-white transition-colors">
            <input
              type="checkbox"
              checked={messages.length > 0 && selectedIds.size === messages.length}
              onChange={handleSelectAll}
              disabled={messages.length === 0}
              className="w-4 h-4 rounded border-slate-600 bg-slate-700 text-purple-500 focus:ring-purple-500 focus:ring-offset-0 cursor-pointer disabled:opacity-50"
            />
            <span>All</span>
          </label>
          <button
            onClick={handleDeleteSelected}
            disabled={selectedIds.size === 0}
            className="px-3 py-1.5 text-sm font-medium rounded-lg border transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed border-red-500/30 text-red-400 hover:bg-red-500/10 hover:border-red-500/50"
          >
            Delete ({selectedIds.size})
          </button>
          <span className="ml-auto text-xs text-slate-500">{messages.length} emails</span>
        </div>

        {/* Email List */}
        <div className="flex-1 overflow-y-auto">
          {messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-center p-6">
              <div className="w-16 h-16 rounded-full bg-slate-700/50 flex items-center justify-center mb-4">
                <span className="text-2xl">👤</span>
              </div>
              <p className="text-slate-400 font-medium">No customer emails yet</p>
              <p className="text-slate-500 text-sm mt-1">Trigger an accident to see emails appear here</p>
            </div>
          ) : (
            messages.map((msg) => (
              <div
                key={msg.id}
                onClick={() => setSelectedId(msg.id)}
                className={`flex items-start gap-3 px-4 py-3 cursor-pointer transition-all duration-150 border-l-2 ${
                  selectedId === msg.id
                    ? 'bg-purple-500/10 border-l-purple-500'
                    : selectedIds.has(msg.id)
                    ? 'bg-amber-500/5 border-l-transparent hover:bg-slate-700/30'
                    : 'border-l-transparent hover:bg-slate-700/30'
                }`}
              >
                <input
                  type="checkbox"
                  checked={selectedIds.has(msg.id)}
                  onClick={(e) => handleToggleSelect(msg.id, e)}
                  onChange={() => {}}
                  className="w-4 h-4 mt-0.5 rounded border-slate-600 bg-slate-700 text-purple-500 focus:ring-purple-500 focus:ring-offset-0 cursor-pointer flex-shrink-0"
                />
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-white truncate">
                    {msg.customerName || 'Customer'}
                  </div>
                  <div className="flex items-center justify-between mt-1">
                    <span className="text-xs font-mono bg-slate-700/50 text-slate-300 px-1.5 py-0.5 rounded">
                      {msg.claimReference}
                    </span>
                    <span className="text-xs text-slate-500">
                      {new Date(msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Right Panel - Email Detail */}
      <div className="flex flex-col overflow-hidden bg-slate-800/10">
        {selectedMessage ? (
          <>
            {/* Email Header */}
            <div className="px-6 py-4 border-b border-slate-700/50 bg-slate-800/20">
              <h2 className="text-lg font-semibold text-white mb-2">{selectedMessage.subject}</h2>
              <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
                <div className="text-slate-400">
                  <span className="text-slate-500">Claim:</span>{' '}
                  <span className="font-mono text-slate-300">{selectedMessage.claimReference}</span>
                </div>
                <div className="text-slate-400">
                  <span className="text-slate-500">Sent:</span>{' '}
                  <span className="text-slate-300">{new Date(selectedMessage.sentAt).toLocaleString()}</span>
                </div>
              </div>
            </div>

            {/* Email Body */}
            <div className="flex-1 overflow-auto p-6">
              <div className="bg-white rounded-xl shadow-lg overflow-hidden">
                <EmailViewer html={selectedMessage.body} />
              </div>
            </div>
          </>
        ) : (
          <div className="flex flex-col items-center justify-center h-full text-center">
            <div className="w-20 h-20 rounded-full bg-slate-700/30 flex items-center justify-center mb-4">
              <span className="text-3xl">📩</span>
            </div>
            <p className="text-slate-400 font-medium">Select an email to view</p>
            <p className="text-slate-500 text-sm mt-1">Choose from the list on the left</p>
          </div>
        )}
      </div>
    </div>
  );
};
