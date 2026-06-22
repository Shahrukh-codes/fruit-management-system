package com.example.demo.controller;

import com.example.demo.model.Fruit;
import com.example.demo.model.Sale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*") // Crucial for letting your S3 frontend talk to your EC2 backend later
public class FruitController {

    @Autowired
    private EntityManager entityManager;

    // 1. AWS Load Balancer Health Route
    @GetMapping("/health")
    public String healthCheck() { 
        return "OK"; 
    }

    // 2. Add New Fruit
    @PostMapping("/fruits")
    @Transactional
    public String addFruit(@RequestBody Fruit fruit) {
        entityManager.persist(fruit);
        return "Fruit added to inventory successfully!";
    }

    // 3. Read Total Stock
    @GetMapping("/fruits")
    public List<Fruit> getAllFruits() {
        return entityManager.createQuery("SELECT f FROM Fruit f", Fruit.class).getResultList();
    }

    // 4. Process a Transaction
    @PostMapping("/sales")
    @Transactional
    public String recordSale(@RequestParam Long fruitId, @RequestParam Integer qty) {
        Fruit fruit = entityManager.find(Fruit.class, fruitId);
        if (fruit == null) return "Error: Fruit not found";
        if (fruit.getCurrentStock() < qty) return "Error: Insufficient stock!";

        // Deduct inventory numbers
        fruit.setCurrentStock(fruit.getCurrentStock() - qty);
        entityManager.merge(fruit);

        // Save transactional ledger item
        Sale sale = new Sale();
        sale.setFruit(fruit);
        sale.setQuantitySold(qty);
        sale.setTotalRevenue(fruit.getSellingPrice() * qty);
        sale.setSaleDate(LocalDate.now());
        entityManager.persist(sale);

        return "Transaction complete. Stock numbers adjusted.";
    }

    // 5. Calculate Dynamic Profit/Loss Reports over Custom Timelines
    @GetMapping("/analytics/report")
    public Map<String, Object> getFinancialReport(
            @RequestParam String startDate, 
            @RequestParam String endDate) {
        
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        Query query = entityManager.createQuery(
            "SELECT s FROM Sale s WHERE s.saleDate BETWEEN :start AND :end", Sale.class);
        query.setParameter("start", start);
        query.setParameter("end", end);
        List<Sale> salesInPeriod = query.getResultList();

        Double totalRevenue = 0.0;
        Double totalCostPriceOfSoldGoods = 0.0;
        Integer totalUnitsDistributed = 0;

        for (Sale sale : salesInPeriod) {
            totalRevenue += sale.getTotalRevenue();
            totalCostPriceOfSoldGoods += (sale.getFruit().getCostPrice() * sale.getQuantitySold());
            totalUnitsDistributed += sale.getQuantitySold();
        }

        Double netProfitOrLoss = totalRevenue - totalCostPriceOfSoldGoods;

        Map<String, Object> report = new HashMap<>();
        report.put("periodStart", start);
        report.put("periodEnd", end);
        report.put("totalVolumeSold", totalUnitsDistributed);
        report.put("grossRevenue", totalRevenue);
        report.put("netProfitOrLoss", netProfitOrLoss);
        report.put("summary", netProfitOrLoss >= 0 ? "PROFIT" : "LOSS");

        return report;
    }
}