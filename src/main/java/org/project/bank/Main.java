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


public class Main {
    //Paramètres globaux
    static double HOUR = 3600.0;
    double OPENING_TIME = 10.0 * HOUR; // Heure d'ouverture
    double CLOSING_TIME = 16.0 * HOUR; // Heure de fermeture
    int NUM_PERIODS = 3; // Nombre de périodes de la journée
    int period; // Période actuelle
    double[] ARRIVAL_RATES; // Taux d'arrivés des clients par période
    int[] NUM_CASHIERS; // Nombre de caissiers disponibles par période.
    int[] NUM_ADVISORS; // Nombre de conseillers disponibles par période.
    double r_rate; // Probablité qu'un conseiller ait un rendez-vous
    double p_absent; // Probablité qu'un client de type B ne se présente pas
    double tA_mean, tA_std; // Moyenne et écart-type de la durée de service pour les clients de type A.
    double muA, sigmaA; //..
    double tB_mean, tB_std; // Moyenne et écart-type de la durée de service pour les clients de type B.
    double muB, sigmaB; //..
    double r_mean, r_std; //  Moyenne et écart-type des retards pour les clients de type B.
    double muR, sigmaR; //..
    double s; // Seuille pour qu'un conseiller puisse servire un client de type A


    //Variables génératives
    ExponentialGen[] genArrivalA;
    RandomVariateGen genServiceA;

    // Listes

    LinkedList<Customer> waitListA = new LinkedList<> ();


    LinkedList<Customer> servList = new LinkedList<> ();

    List <Cashier> Cashiers = new ArrayList<>();

    // Initialisation des accumulateurs et tallys pour les statistiques
    Tally waitTimesA = new Tally("Temps d'attente des clients A");

    Accumulate totWaitA  = new Accumulate ("Taille de la queue des clients de type A");
    Accumulate totWaitTimeA = new Accumulate("Temps d'attente total des clients A");


    public static class Customer{
        double arrivTime;
        double servTime;
        int type;

        public Customer(int type) {
            this.type = type;
        }


    }

    public static class Cashier {
        // Class pour pouvoir avoir le nombre de cashiers
    }


    // Méthode pour créer les caissiers en fonction des périodes
    private void createCashiers() {
        for (int i = 0; i < NUM_PERIODS; i++) {
            int numCashiers = NUM_CASHIERS[i];

            for (int j = 0; j < numCashiers; j++) {
                Cashier cashier = new Cashier();
                Cashiers.add(cashier);
            }
        }
    }


    // Horaires de début de chaque période
    //double[] PERIOD_START_TIMES = {10.0 * HOUR, 12.0 * HOUR, 14.0 * HOUR};

    public Main(int[] numCashiers, int[] numAdvisors, double[] arrivalRates,
                double muA, double sigmaA, double muR, double sigmaR,
                double muB, double sigmaB, double r_rate, double p_absent,
                double s){

        NUM_CASHIERS = numCashiers;
        NUM_ADVISORS = numAdvisors;
        ARRIVAL_RATES = arrivalRates;
        this.muA = muA;
        this.sigmaA = sigmaA;
        this.muR = muR;
        this.sigmaR = sigmaR;
        this.muB = muB;
        this.sigmaB = sigmaB;
        this.r_rate = r_rate;
        this.p_absent = p_absent;
        this.s = s;

        // Créer les conseillers et les caissiers

        createCashiers();



        // Créer les générateurs de temps d'arrivée
        // Initialiser les générateurs de temps d'arrivée pour chaque période
        genArrivalA = new ExponentialGen[NUM_PERIODS];
        for (int i = 0; i < NUM_PERIODS; i++) {
            genArrivalA[i] = new ExponentialGen(new MRG32k3a(), 1.0 / ARRIVAL_RATES[i]);
        }
        //genServ = new ExponentialGen (new MRG32k3a(), mu);
        //muA = Math.log(tA_mean) - 0.5 * (1 + tA_std * tA_std / (tA_mean * tA_mean));
        //sigmaA = Math.sqrt(Math.log(1 + tA_std * tA_std / (tA_mean * tA_mean)));

        genServiceA = new LognormalGen(new MRG32k3a(), muA, sigmaA);



    }


    // Méthode principale pour lancer la simulation
    public void simulate(double simulationDays) {
        Sim.init();
        double simulationEndTime = CLOSING_TIME + simulationDays * HOUR * 24; // Calculer la fin de la simulation

        // Planifier un événement de fin de simulation à la nouvelle heure de fin
        new EndOfSim().schedule(simulationEndTime);

// Planifier le premier événement d'arrivée des clients de type A après l'heure d'ouverture
        double firstArrivalTime = OPENING_TIME + genArrivalA[0].nextDouble();
        while (firstArrivalTime > CLOSING_TIME) {
            firstArrivalTime = OPENING_TIME + genArrivalA[0].nextDouble();
        }
        new Arrival().schedule(firstArrivalTime);

        Sim.start();
    }

    class Arrival extends Event{
        public void actions(){
            // Générer l'arrivée du prochain client de type A
            new Arrival().schedule (genArrivalA[period].nextDouble());

            // Vérifier si l'heure actuelle est pendant les heures d'ouverture
            if(Sim.time() <= CLOSING_TIME) {
                // Générer un client de type A si l'heure d'arrivée est avant l'heure de fermeture
                Customer custA = new Customer(0);
                custA.arrivTime = Sim.time();
                custA.servTime = genServiceA.nextDouble();

                if (servList.size() < NUM_CASHIERS[period]) {
                    // Il y a des serveurs disponibles, commencer le service immédiatement
                    waitTimesA.add(0.0);
                    servList.addLast(custA);
                    new DepartureTypeA().schedule(custA.servTime);
                } else {
                    waitListA.addLast(custA);
                    totWaitA.update(waitListA.size());
                }
            }
        }

    }

    class DepartureTypeA extends Event {
        public void actions() {
            servList.removeFirst(); // Retirer le client qui part du service
            if (waitListA.size() > 0) {
                Customer nextCustA = waitListA.removeFirst();
                waitTimesA.add(Sim.time() - nextCustA.arrivTime);
                totWaitTimeA.update (waitListA.size());
                servList.addLast(nextCustA);
                new DepartureTypeA().schedule(Sim.time() + nextCustA.servTime);


            }


        }
    }


    class EndOfSim extends Event {
        public void actions() {
            if (Sim.time() >= CLOSING_TIME) {

                Sim.stop(); // Arrêter la simulation lorsque tous les clients ont été servis
            }
        }
    }

    // Méthode pour afficher les résultats de la simulation


    public static void main(String[] args) {
        // Définir les valeurs spécifiées
        int[] numCashiers = { 3, 4, 3 };
        int[] numAdvisors = { 2, 3, 3 };
        double[] arrivalRates = { 20.0 / HOUR, 35.0 / HOUR, 28.0 / HOUR };
        double muA = 200.0;
        double sigmaA = 60.0;
        double muR = 100.0;
        double sigmaR = 90.0;
        double muB = 20.0 * 60.0;
        double sigmaB = 8.0 * 60.0;
        double r_rate = 0.8;
        double p_absent = 0.05;
        double s = 10.0 * 60.0;

        Main bank = new Main(numCashiers, numAdvisors, arrivalRates, muA, sigmaA, muR, sigmaR,
                muB, sigmaB, r_rate, p_absent, s);

        bank.simulate(3); //Nombre de jours
        System.out.println (bank.waitTimesA.report());


    }


}
