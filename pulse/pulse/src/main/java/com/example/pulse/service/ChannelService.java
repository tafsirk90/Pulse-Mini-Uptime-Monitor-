package com.example.pulse.service;

import com.example.pulse.dto.Dtos.ChannelRequest;
import com.example.pulse.dto.Dtos.ChannelResponse;
import com.example.pulse.dto.Dtos.MessageResponse;
import com.example.pulse.entity.AlertChannel;
import com.example.pulse.enums.ChannelType;
import com.example.pulse.exception.BadRequestException;
import com.example.pulse.exception.ResourceNotFoundException;
import com.example.pulse.net.UrlGuard;
import com.example.pulse.repository.AlertChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private static final int MAX_PER_USER = 5;

    private final AlertChannelRepository channels;
    private final AlertService alertService;
    private final UrlGuard guard;

    @Transactional(readOnly = true)
    public List<ChannelResponse> list(Long userId) {
        return channels.findByUserIdOrderByIdAsc(userId).stream().map(ChannelService::toResponse).toList();
    }

    @Transactional
    public ChannelResponse create(Long userId, ChannelRequest r) {
        if (channels.countByUserId(userId) >= MAX_PER_USER) {
            throw new BadRequestException("You can have up to " + MAX_PER_USER + " alert channels");
        }
        String target = r.target().trim();
        if (r.type() == ChannelType.EMAIL) {
            if (!target.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new BadRequestException("Enter a valid email address");
            }
        } else {
            guard.requirePublic(target);   // webhooks must also point at a public address
        }
        return toResponse(channels.save(AlertChannel.builder().userId(userId).type(r.type()).target(target).build()));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        channels.delete(owned(userId, id));
    }

    public MessageResponse test(Long userId, Long id) {
        AlertChannel channel = owned(userId, id);
        try {
            String note = alertService.deliver(channel, "[TEST] Pulse alert",
                    "[TEST] This is a test alert from Pulse. Your channel works.");
            return new MessageResponse(note);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            throw new BadRequestException("Delivery failed: " + root.getMessage());
        }
    }

    private AlertChannel owned(Long userId, Long id) {
        return channels.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found"));
    }

    private static ChannelResponse toResponse(AlertChannel c) {
        return new ChannelResponse(c.getId(), c.getType(), c.getTarget(), c.getCreatedAt());
    }
}
