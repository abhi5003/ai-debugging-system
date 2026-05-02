package com.aidbg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Unified scoring configuration that defines both the score weights
 * and the routing thresholds in one place.
 *
 * Previously PriorityScorer assigned CRITICAL → 80 and SeverityRouter
 * had criticalThreshold: 80 as separate concerns. This couples them
 * together so changing one without the other is impossible to do by
 * accident.
 */
@Configuration
@ConfigurationProperties(prefix = "processor.score")
public class ScoringConfig {

    /** Base score for CRITICAL priority incidents */
    private int criticalBase = 80;

    /** Base score for HIGH priority incidents */
    private int highBase = 60;

    /** Base score for MEDIUM priority incidents */
    private int mediumBase = 40;

    /** Base score for LOW priority incidents */
    private int lowBase = 20;

    /** Base score for unknown/unmapped priorities */
    private int unknownBase = 10;

    /** Error rate > this adds the error boost */
    private double errorRateHighThreshold = 10.0;

    /** Boost amount when error rate is very high */
    private int errorRateHighBoost = 15;

    /** Error rate > this (but below high threshold) adds this boost */
    private double errorRateMediumThreshold = 5.0;

    /** Boost amount for medium error rate */
    private int errorRateMediumBoost = 8;

    /** Boost per open problem (max total = openProblemMaxBoost) */
    private int boostPerOpenProblem = 5;

    /** Maximum total boost from open problems */
    private int openProblemMaxBoost = 15;

    /** Score >= this routes to HIGH severity */
    private int criticalThreshold = 80;

    /** Score >= this routes to MEDIUM severity */
    private int highThreshold = 50;

    public int getBaseScore(com.aidbg.model.Priority priority) {
        if (priority == null) return unknownBase;
        return switch (priority) {
            case CRITICAL -> criticalBase;
            case HIGH     -> highBase;
            case MEDIUM   -> mediumBase;
            case LOW      -> lowBase;
            default       -> unknownBase;
        };
    }

    // ── Getters & Setters ──────────────────────────────────────────

    public int getCriticalBase()          { return criticalBase; }
    public void setCriticalBase(int v)    { this.criticalBase = v; }
    public int getHighBase()              { return highBase; }
    public void setHighBase(int v)        { this.highBase = v; }
    public int getMediumBase()            { return mediumBase; }
    public void setMediumBase(int v)      { this.mediumBase = v; }
    public int getLowBase()               { return lowBase; }
    public void setLowBase(int v)         { this.lowBase = v; }
    public int getUnknownBase()           { return unknownBase; }
    public void setUnknownBase(int v)     { this.unknownBase = v; }
    public double getErrorRateHighThreshold()  { return errorRateHighThreshold; }
    public void setErrorRateHighThreshold(double v) { this.errorRateHighThreshold = v; }
    public int getErrorRateHighBoost()    { return errorRateHighBoost; }
    public void setErrorRateHighBoost(int v) { this.errorRateHighBoost = v; }
    public double getErrorRateMediumThreshold() { return errorRateMediumThreshold; }
    public void setErrorRateMediumThreshold(double v) { this.errorRateMediumThreshold = v; }
    public int getErrorRateMediumBoost()  { return errorRateMediumBoost; }
    public void setErrorRateMediumBoost(int v) { this.errorRateMediumBoost = v; }
    public int getBoostPerOpenProblem()   { return boostPerOpenProblem; }
    public void setBoostPerOpenProblem(int v) { this.boostPerOpenProblem = v; }
    public int getOpenProblemMaxBoost()   { return openProblemMaxBoost; }
    public void setOpenProblemMaxBoost(int v) { this.openProblemMaxBoost = v; }
    public int getCriticalThreshold()     { return criticalThreshold; }
    public void setCriticalThreshold(int v) { this.criticalThreshold = v; }
    public int getHighThreshold()         { return highThreshold; }
    public void setHighThreshold(int v)   { this.highThreshold = v; }
}
