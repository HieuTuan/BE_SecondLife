package com.secondlife.secondlife;

import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.repository.NegotiationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class DbInspector4 implements CommandLineRunner {

    @Autowired
    private NegotiationRepository negotiationRepository;

    @Override
    public void run(String... args) throws Exception {
        UUID postId = UUID.fromString("61db6c15-4674-42b7-a384-5a3962635467"); // Need real DB ids
        UUID buyerId = UUID.fromString("c0a80112-91d8-1111-8191-d8525b6c0000"); // Assuming from previous logs
        
        System.out.println("--- TESTING REPOSITORY CALLS ---");
        try {
            System.out.println("Calling existsByPostIdAndBuyerIdAndStatusIn");
            negotiationRepository.existsByPostIdAndBuyerIdAndStatusIn(postId, buyerId, List.of(NegotiationStatus.PENDING, NegotiationStatus.ACCEPTED));
        } catch (Exception e) {
            System.out.println("ERROR in existsBy: " + e.getMessage());
        }

        try {
            System.out.println("Calling findMaxRejectedPrice");
            negotiationRepository.findMaxRejectedPrice(postId, buyerId);
        } catch (Exception e) {
            System.out.println("ERROR in max: " + e.getMessage());
        }

        try {
            System.out.println("Calling countRejectedNegotiations");
            negotiationRepository.countRejectedNegotiations(postId, buyerId);
        } catch (Exception e) {
            System.out.println("ERROR in count: " + e.getMessage());
        }
        System.out.println("--- FINISHED ---");
    }
}
