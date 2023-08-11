package org.project.bank;

/*
 * @author Isabelle Olive Kantoussan & Mouhamadou Mansour Kholle
 */

import umontreal.ssj.simevents.*;
import umontreal.ssj.rng.*;
import umontreal.ssj.randvar.*;
import umontreal.ssj.stat.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


public class Bank {
    //Paramètres globaux
    double HOUR = 3600.0;
    double OPENING_TIME = 10.0 * HOUR; // Heure d'ouverture
    double CLOSING_TIME = 16.0 * HOUR; // Heure de fermeture
    int NUM_PERIODS = 3; // Nombre de périodes de la journée
    double[] ARRIVAL_RATES; // Taux d'arrivés des clients par période
    int[] NUM_CASHIERS; // Nombre de caissiers disponibles par période.
    int[] NUM_ADVISORS; // Nombre de conseillers disponibles par période.
    double r_rate; // Probablité qu'un conseiller ait un rendez-vous
    double p_absent; // Probablité qu'un client de type B ne se présente pas
    double tA_mean, tA_std; // Moyenne et écart-type de la durée de service pour les clients de type A.
    double tB_mean, tB_std; // Moyenne et écart-type de la durée de service pour les clients de type B.
    double r_mean, r_std; //  Moyenne et écart-type des retards pour les clients de type B.

    //Variables génératives
    RandomVariateGen genArrivalA;
    RandomVariateGen genArrivalB;
    RandomVariateGen genServiceA;
    RandomVariateGen genServiceB;
    RandomVariateGen genDelayB;

    // Listes
    List <Customer> customers = new ArrayList<>();
    List <Advisor> Advisors = new ArrayList<>();


    public class Customer{
        double arrivTime;
        double servTime;
        int type;
        double patienceTime = 0.0; // Temps d'attente depuis l'heure du RV our les clients de type B
        double delay = 0.0; // Retart/avance par rapport à l'heure du rendez-vous
    }

    public class Advisor{

    }

    public Bank(){}


}
