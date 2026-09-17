package edu.cit.verano.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller exposing the notification feed to the React frontend.
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(
    origins = {
        "http://localhost:5173",
        "http://localhost:3000",
        "http://127.0.0.1:5173"
    },
    allowedHeaders = "*",
    methods = {RequestMethod.GET, RequestMethod.OPTIONS}
)
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications() {
        return ResponseEntity.ok(notificationRepository.findAllByOrderByCreatedAtDesc());
    }
}

