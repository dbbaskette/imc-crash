import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { AgentStatusMessage, CustomerDetectedMessage } from '../types';

// Use environment variable or default to orchestrator (port 8080) where FNOL processing happens
// The orchestrator has WebSocket endpoints and broadcasts status updates during GOAP agent execution
const WEBSOCKET_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws-crash';

class WebSocketService {
  private client: Client | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectDelay = 3000;

  connect(
    onAgentStatus: (message: AgentStatusMessage) => void,
    onCustomerDetected: (message: CustomerDetectedMessage) => void,
    onConnected?: () => void,
    onError?: (error: Error) => void
  ): void {
    this.client = new Client({
      webSocketFactory: () => new SockJS(WEBSOCKET_URL) as any,
      debug: (str) => {
        console.log('STOMP:', str);
      },
      reconnectDelay: this.reconnectDelay,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('WebSocket connected');
        this.reconnectAttempts = 0;
        
        // Subscribe to agent status updates
        this.client?.subscribe('/topic/agent-status', (message: IMessage) => {
          const data = JSON.parse(message.body) as AgentStatusMessage;
          onAgentStatus(data);
        });

        // Subscribe to customer detected events
        this.client?.subscribe('/topic/customer-detected', (message: IMessage) => {
          const data = JSON.parse(message.body) as CustomerDetectedMessage;
          onCustomerDetected(data);
        });

        onConnected?.();
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
        const error = new Error(frame.headers['message'] || 'WebSocket error');
        onError?.(error);
      },
      onWebSocketError: (event) => {
        console.error('WebSocket error:', event);
        onError?.(new Error('WebSocket connection error'));
      },
      onWebSocketClose: () => {
        console.log('WebSocket closed');
        this.reconnectAttempts++;
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
          console.error('Max reconnect attempts reached');
          onError?.(new Error('Failed to connect after multiple attempts'));
        }
      },
    });

    this.client.activate();
  }

  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      console.log('WebSocket disconnected');
    }
  }

  isConnected(): boolean {
    return this.client?.connected || false;
  }
}

export const websocketService = new WebSocketService();
