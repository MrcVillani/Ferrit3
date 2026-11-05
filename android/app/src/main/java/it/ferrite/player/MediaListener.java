package it.ferrite.player;

import android.service.notification.NotificationListenerService;

/**
 * Servizio vuoto: esiste solo perché Android concede l'elenco dei player attivi
 * (MediaSessionManager.getActiveSessions) alle app con l'accesso alle notifiche.
 */
public class MediaListener extends NotificationListenerService {
}
