import axios from 'axios';
import type { Message } from '../types';

// Use environment variable or default to localhost for development
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8085/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const messageAPI = {
  /**
   * Get all adjuster messages (FNOL emails, notifications)
   */
  getAdjusterMessages: async (): Promise<Message[]> => {
    const response = await apiClient.get<Message[]>('/messages/adjuster');
    return response.data;
  },

  /**
   * Get all customer messages, optionally filtered by customer name
   */
  getCustomerMessages: async (customerName?: string): Promise<Message[]> => {
    const response = await apiClient.get<Message[]>('/messages/customer', {
      params: { customerName },
    });
    return response.data;
  },

  /**
   * Get all messages for a specific claim
   */
  getClaimMessages: async (claimReference: string): Promise<Message[]> => {
    const response = await apiClient.get<Message[]>(`/messages/claim/${claimReference}`);
    return response.data;
  },

  /**
   * Get all messages (for debugging)
   */
  getAllMessages: async (): Promise<Message[]> => {
    const response = await apiClient.get<Message[]>('/messages');
    return response.data;
  },

  /**
   * Delete all messages (for demo reset)
   */
  deleteAllMessages: async (): Promise<void> => {
    await apiClient.delete('/messages');
  },

  /**
   * Delete all adjuster messages
   */
  deleteAdjusterMessages: async (): Promise<void> => {
    await apiClient.delete('/messages/adjuster');
  },

  /**
   * Delete all customer messages
   */
  deleteCustomerMessages: async (): Promise<void> => {
    await apiClient.delete('/messages/customer');
  },

  /**
   * Delete a specific message by ID
   */
  deleteMessage: async (id: number): Promise<void> => {
    await apiClient.delete(`/messages/${id}`);
  },
};
