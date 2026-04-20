import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_BASE } from './api';

export function connectDocumentTopic(documentId, onUpdate) {
  const wsBase = API_BASE.replace(/^http/, '');
  const client = new Client({
    webSocketFactory: () => new SockJS(`http${wsBase}/ws`),
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe(`/topic/documents/${documentId}`, (message) => {
        onUpdate(JSON.parse(message.body));
      });
    }
  });
  client.activate();
  return () => client.deactivate();
}
