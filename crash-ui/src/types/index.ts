// TypeScript types for the CRASH UI

export interface AgentStatusMessage {
  agent: string;
  action: string;
  status: 'STARTED' | 'COMPLETED' | 'FAILED';
  timestamp: string;
}

export interface CustomerDetectedMessage {
  claimReference: string;
  policyId: number;
  customerName: string;
  phone: string;
  email: string;
  timestamp: string;
}

export interface Message {
  id: number;
  messageType: 'EMAIL' | 'SMS' | 'PUSH';
  recipientType: 'ADJUSTER' | 'CUSTOMER';
  recipientIdentifier: string | null;
  claimReference: string | null;
  subject: string | null;
  body: string;
  sentAt: string;
  customerName: string | null;
  policyId: number | null;
  createdAt: string;
}

export interface Customer {
  name: string;
  claimReference: string;
  phone: string;
  email: string;
  timestamp: string;
}
