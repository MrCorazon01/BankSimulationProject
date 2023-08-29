package org.project.bank;

/*
 * @author Isabelle Olive Kantoussan & Mouhamadou Mansour Kholle
 */

import umontreal.ssj.simevents.*;
import umontreal.ssj.rng.*;
import umontreal.ssj.randvar.*;
import umontreal.ssj.stat.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.swing.*;
import java.util.Arrays;
import java.util.Scanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;


/**
 * Cette classe représente un simulateur de banque.
 * Elle permet de simuler le fonctionnement d'une banque avec différents paramètres.
 */
public class Bank {
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
    double tB_mean, tB_std; // Moyenne et écart-type de la durée de service pour les clients de type B.
    double r_mean, r_std; //  Moyenne et écart-type des retards pour les clients de type B.
    double s; // Seuille pour qu'un conseiller puisse servire un client de type A
    static int nbDay; //Nombre de jours de la simulation

    //Variables génératives
    ExponentialGen[] genArrivalA;
    RandomVariateGen genServiceA;
    RandomVariateGen genServiceB;
    RandomVariateGen genDelayB;

    // Listes pour les clients de type A et B
    LinkedList<Customer> waitListA = new LinkedList<> ();
    LinkedList<Customer> waitListB = new LinkedList<> ();

    // Listes pour les caissiers et les conseillers
    LinkedList<Customer> servList = new LinkedList<> ();
    List <Advisor> Advisors = new ArrayList<>();
    List <Cashier> Cashiers = new ArrayList<>();

    // Initialisation des accumulateurs et tallys pour les statistiques
    Tally waitTimesA = new Tally("Temps d'attente des clients A");
    Tally waitTimesB = new Tally("Temps d'attente des clients B");

    Accumulate totWaitA  = new Accumulate ("Taille de la queue des clients de type A");

    // Variable des temps d'attente
   double totWaitTimeA = 0.0;
   double totWaitTimeB = 0.0;
    int numClientsA = 0;
    int numClientsB = 0;
    double[] totalWaitTimesA; // Sommes des temps d'attente total de clients de type A par jour
    double[] totalWaitTimesB; // Sommes des temps d'attente total de clients de type B par jour
    int currentDay = 0; // Variable pour suivre le jour en cours
    static List<Double> dailyWaitTimesA = new ArrayList<>(); // Liste des temps d'attente moyens par jour pour les clients de type A
    static List<Double> dailyWaitTimesB = new ArrayList<>(); // Liste des temps d'attente moyens par jour pour les clients de type B


    /**
     * Cette classe interne représente un client de la banque.
     * Elle stocke des informations sur le client, telles que son type, son temps d'arrivée, etc.
     */
    public class Customer{
        double arrivTime;
        double servTime;
        int type;

        //Les détails de rendez-vous pour le type de client de type B
        Advisor assignedAdvisor; // Le conseiller assigné au client de type B
        Appointment appointment; // Le rendez-vous du client de type B

        public Customer(int type) {
            this.type = type;
        }


        public void createAppointment(double appointmentTime, Advisor advisor) {
            // Créer le rendez-vous pour le client de type B
            this.appointment = new Appointment(advisor, appointmentTime);
            this.assignedAdvisor = advisor;


        }
    }


    /**
     * Cette classe interne représente un caissier de la banque.
     * Elle peut être utilisée pour compter le nombre de caissiers par période.
     */
    public static class Cashier {
        // Class pour pouvoir avoir le nombre de cashiers
    }


    /**
     * Cette classe interne représente un conseiller de la banque.
     * Elle gère la disponibilité des conseillers et leurs rendez-vous.
     */
    // Dans la classe Advisor
    public class Advisor {
        double busyUntil = 0.0; // Heure à laquelle le conseiller sera libre
        boolean hasAppointment = false; // Indique si le conseiller a un rendez-vous en cours
        List<Appointment> appointments = new ArrayList<>(); // Liste des rendez-vous du conseiller

        public boolean canServe(double currentTime) {
            return currentTime >= busyUntil; // Le conseiller est disponible s'il n'est pas occupé
        }


        // Méthode pour servir un client de type A ou B
        public void serve(Customer customer) {
            if (canServe(Sim.time())) {
                if (customer.type == 0) { // Client de type A
                    busyUntil = Sim.time() + customer.servTime;
                } else if (customer.type == 1) { // Client de type B avec rendez-vous
                    hasAppointment = true;
                    double appointmentTime = customer.appointment.appointmentTime;
                    double actualArrivalTime = customer.arrivTime;

                    if (actualArrivalTime >= appointmentTime) {
                        busyUntil = actualArrivalTime + customer.servTime;
                    } else {
                        busyUntil = appointmentTime + customer.servTime;
                    }

                    appointments.add(customer.appointment); // Ajouter le rendez-vous à la liste du conseiller
                }
            }
        }
        // Méthode pour terminer un rendez-vous
        public void finishAppointment() {
            hasAppointment = false;
            busyUntil = Sim.time(); // Le conseiller est libre dès qu'il a fini de servir
        }

        // Méthode pour vérifier si le conseiller peut servir un client de type A
        public boolean canServeTypeA(double currentTime) {
            // Vérifier si le conseiller peut servir un client de type A
            if (canServe(currentTime)) {
                for (Customer customer : waitListA) {
                    // Si le conseiller peut servir le prochain client de type A immédiatement
                    if (!this.hasAppointment && currentTime + s >= customer.arrivTime) {
                        return true;
                    }
                }
            }
            return false;
        }

    }


    /**
     * Cette classe interne représente un rendez-vous entre un conseiller et un client de type B.
     */
    public class Appointment {
        Advisor advisor;
        double appointmentTime;


        public Appointment(Advisor advisor, double appointmentTime) {
            this.advisor = advisor;
            this.appointmentTime = appointmentTime;
        }





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

    // Méthode pour créer les conseillers en fonction des périodes
    private void createAdvisors() {
        for (int i = 0; i < NUM_PERIODS; i++) {
            int numAdvisors = NUM_ADVISORS[i];

            for (int j = 0; j < numAdvisors; j++) {
                Advisor advisor = new Advisor();
                Advisors.add(advisor);
            }
        }
    }

    // Méthode pour générer les rendez-vous pour les clients de type B
    private void generateAppointmentsForTypeB(double simulationStartTime){
        // génération des rendez-vous pour les clients de type B
        for (Advisor advisor : Advisors) {
            for (int j = 0; j < 12; j++) {
                if (Math.random() < r_rate) { // Si le conseiller a un rendez-vous
                    double appointmentTime = PERIOD_START_TIMES[period] + j * 0.5 * HOUR;

                    // Générer un retard aléatoire basé sur μr et sr
                    double delay = genDelayB.nextDouble();

                    // L'heure du rendez-vous sera l'heure de début de la plage horaire + retard aléatoire
                    double actualAppointmentTime = appointmentTime + delay;

                    // Vérifier si le client de type B ne se présente pas avec probabilité p_absent
                    if (Math.random() >= p_absent) {
                        Customer custB = new Customer(1);
                        custB.arrivTime = actualAppointmentTime;
                        custB.servTime = genServiceB.nextDouble();
                        custB.createAppointment(appointmentTime, advisor);

                        // Ajouter le client B à la liste d'attente waitListB
                        waitListB.addLast(custB);

                        // Planifier le départ du client B en fonction de son temps de service
                        if (waitListB.size() == 1) { // Vérifier si c'est le premier client de type B dans la liste
                            new DepartureTypeB().schedule(actualAppointmentTime + custB.servTime);
                        }
                    }
                }
            }
        }
    }

    // Horaires de début de chaque période
    double[] PERIOD_START_TIMES = {10.0 * HOUR, 12.0 * HOUR, 14.0 * HOUR};


    public Bank(){

        try{
            BufferedReader br = new BufferedReader(new FileReader("parameters.txt"));
            String line;

            while ((line = br.readLine()) != null) {
                // Traitez chaque ligne ici, par exemple, en divisant la ligne en clé et en valeur
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    switch (key) {
                        case "numCashiers":
                            // Parser la valeur en un tableau d'entiers
                            String[] cashiers = value.split(",");
                            NUM_CASHIERS = Arrays.stream(cashiers)
                                    .mapToInt(Integer::parseInt)
                                    .toArray();
                            break;
                        case "numAdvisors":
                            // Parser la valeur en un tableau d'entiers
                            String[] advisors = value.split(",");
                            NUM_ADVISORS = Arrays.stream(advisors)
                                    .mapToInt(Integer::parseInt)
                                    .toArray();
                            break;
                        case "arrivalRates":
                            // Parser la valeur en un tableau de doubles
                            String[] rates = value.split(",");
                            ARRIVAL_RATES = Arrays.stream(rates)
                                    .mapToDouble(Double::parseDouble)
                                    .toArray();
                            break;
                        case "tA_mean":
                            tA_mean = Double.parseDouble(value);
                            break;
                        case "tA_std":
                            tA_std = Double.parseDouble(value);
                            break;
                        case "r_mean":
                            r_mean = Double.parseDouble(value);
                            break;
                        case "r_std":
                            r_std = Double.parseDouble(value);
                            break;
                        case "tB_mean":
                            tB_mean = Double.parseDouble(value);
                            break;
                        case "tB_std":
                            tB_std = Double.parseDouble(value);
                            break;
                        case "r_rate":
                            r_rate = Double.parseDouble(value);
                            break;
                        case "p_absent":
                            p_absent = Double.parseDouble(value);
                            break;
                        case "s":
                            s = Double.parseDouble(value);
                            break;
                        case "nbDay":
                            nbDay = Integer.parseInt(value);
                            break;
                        default:
                            System.err.println("Paramètre non reconnu : " + key);
                    }
                }
            }
            br.close();
        }catch (IOException e) {
                e.printStackTrace();
            }


        // Créer les conseillers et les caissiers
        createAdvisors();
        createCashiers();



        // Créer les générateurs de temps d'arrivée
        // Initialiser les générateurs de temps d'arrivée pour chaque période
        genArrivalA = new ExponentialGen[NUM_PERIODS];
        for (int i = 0; i < NUM_PERIODS; i++) {
            genArrivalA[i] = new ExponentialGen(new MRG32k3a(), 1.0 / ARRIVAL_RATES[i]);
        }
        double muA = Math.log(tA_mean) - 0.5 * Math.log((1 + tA_std/ (tA_mean * tA_mean)));
        double sigmaA = Math.sqrt(Math.log(1 + tA_std / (tA_mean * tA_mean)));

        genServiceA = new LognormalGen(new MRG32k3a(), muA, sigmaA);


        double muB = Math.log(tB_mean) - 0.5 * Math.log((1 + tB_std/ (tB_mean * tB_mean)));
        double sigmaB = Math.sqrt(Math.log(1 + tB_std / (tB_mean * tB_mean)));

        genServiceB = new LognormalGen(new MRG32k3a(), muB, sigmaB);

        genDelayB = new NormalGen(new MRG32k3a(), r_mean, Math.sqrt(r_std));

        // Initialiser les tableaux des sommes des temps d'attente totaux par jour
        totalWaitTimesA = new double[nbDay];
        totalWaitTimesB = new double[nbDay];

    }



    // Méthode principale pour lancer la simulation
    public void simulate(int numSimulationDays) {
        Sim.init();

        for (currentDay = 0; currentDay < numSimulationDays; currentDay++) {


            double simulationStartTime = OPENING_TIME + currentDay * HOUR * 24; // Début de la simulation pour ce jour
            double simulationEndTime = simulationStartTime + HOUR * 24; // Fin de la simulation pour ce jour

            // Planifier un événement de fin de simulation à la nouvelle heure de fin
            new EndOfDaySim().schedule(simulationEndTime);

            // Planifier le premier événement d'arrivée des clients de type A après l'heure d'ouverture
            double firstArrivalTime = simulationStartTime + genArrivalA[0].nextDouble();
            new Arrival().schedule(firstArrivalTime);

            // Générer les rendez-vous pour les clients de type B pour ce jour
            generateAppointmentsForTypeB(simulationStartTime);

            // Planifier les départs initiaux des clients de type B
            for (Customer custB : waitListB) {
                double actualAppointmentTime = custB.appointment.appointmentTime;
                new DepartureTypeB().schedule(actualAppointmentTime + custB.servTime);
            }

            // Exécuter la simulation pour ce jour
            Sim.start();

            // Calculer et afficher les résultats pour ce jour
            collectResults();
        }
    }



    /**
     * Cette classe interne représente un événement d'arrivée des clients de type A.
     * Elle planifie les arrivées des clients de type A.
     */
    class Arrival extends Event{
        public void actions(){
            // Générer l'arrivée du prochain client de type A
            new Arrival().schedule (genArrivalA[period].nextDouble());

            if (Sim.time() <= CLOSING_TIME) {

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


    /**
     * Cette classe interne représente un événement de départ des clients de type A.
     * Elle gère les départs des clients de type A du service.
     */
    class DepartureTypeA extends Event {
        public void actions() {
            servList.removeFirst(); // Retirer le client qui part du service
            if (waitListA.size() > 0) {
                Customer nextCustA = waitListA.removeFirst();
                double wait = Sim.time() - nextCustA.arrivTime;
                waitTimesA.add(wait);
                totWaitA.update (wait);
                totWaitTimeA += wait;
                numClientsA++;

                // Mettre à jour l'accumulateur de la somme des temps d'attente total de clients de type A pour ce jour
                totalWaitTimesA[currentDay] += wait;

                servList.addLast(nextCustA);
                new DepartureTypeA().schedule(Sim.time() + nextCustA.servTime);



                // Vérifier si un conseiller peut servir le prochain client de type A
                for (Advisor advisor : Advisors) {
                    if (advisor.canServeTypeA(Sim.time()) && !advisor.hasAppointment) {
                        advisor.serve(nextCustA); // Le conseiller prend en charge le client de type A
                        new DepartureTypeA().schedule(Sim.time() + nextCustA.servTime);
                        break; // Sortir de la boucle après avoir trouvé un conseiller disponible
                    }
                }




            }


        }
    }


    /**
     * Cette classe interne représente un événement de départ des clients de type B.
     * Elle gère les départs des clients de type B du service.
     */
    class DepartureTypeB extends Event {
        public void actions() {
            if (!waitListB.isEmpty()) {
                Bank.Customer departedCust = waitListB.removeFirst(); // Retirer le client de waitListB qui part du service

                // Gérer le départ du client de type B
                double appointmentTime = departedCust.appointment.appointmentTime;
                double actualArrivalTime = departedCust.arrivTime;


                // Calculer le retard du client par rapport au rendez-vous
                double delay = actualArrivalTime - appointmentTime;

                // Calculer le temps d'attente en fonction du retard et de l'heure de rendez-vous
                double wait = Math.max(0.0, delay); // Le temps d'attente ne peut pas être négatif

                waitTimesB.add(wait);
                totWaitTimeB += wait;
                numClientsB++;

                // Mettre à jour l'accumulateur de la somme des temps d'attente total de clients de type B pour ce jour
                totalWaitTimesB[currentDay] += wait;

                // Appeler finishAppointment pour le conseiller qui a fini de servir le client de type B
                Advisor advisor = departedCust.appointment.advisor;
                advisor.finishAppointment();

                // Planifier le prochain départ de client de type B si nécessaire
                if (!waitListB.isEmpty()) {
                    Customer nextCustB = waitListB.getFirst();
                    double nextAppointmentTime = nextCustB.appointment.appointmentTime;

                    // Vérifier si le prochain client B est arrivé à l'heure du rendez-vous
                    if (Sim.time() >= nextAppointmentTime) {
                        waitListB.removeFirst(); // Retirer le client de la liste d'attente
                        advisor.appointments.add(nextCustB.appointment);
                        advisor.serve(nextCustB); // Le conseiller prend en charge le client de type B
                        new DepartureTypeB().schedule(Sim.time() + nextCustB.servTime);
                    }
                }
            }
        }
    }


    /**
     * Cette classe interne représente un événement de fin de journée de simulation.
     * Elle arrête la simulation lorsque l'heure de fermeture est atteinte.
     */
    class EndOfDaySim extends Event {
        public void actions() {
            if (Sim.time() >= CLOSING_TIME) {

                Sim.stop(); // Arrêter la simulation lorsque tous les clients ont été servis
            }
        }
    }


    // Méthode pour afficher les résultats de la simulation
    private void collectResults() {
        double avgWaitTimeA = totalWaitTimesA[currentDay] / numClientsA;
        double avgWaitTimeB = totalWaitTimesB[currentDay] / numClientsB;

        dailyWaitTimesA.add(avgWaitTimeA);
        dailyWaitTimesB.add(avgWaitTimeB);


    }

    public void displayResult(){
        // Calcul des moyennes à long terme pour les types A et B
        double sumWaitTimesA = 0.0;
        double sumWaitTimesB = 0.0;

        for (double waitTimeA : dailyWaitTimesA) {
            sumWaitTimesA += waitTimeA;
        }

        for (double waitTimeB : dailyWaitTimesB) {
            sumWaitTimesB += waitTimeB;
        }

        double wa = sumWaitTimesA / nbDay; // Temps d'attente moyen à long terme pour les clients de type A
        double wb = sumWaitTimesB / nbDay; // Temps d'attente moyen à long terme pour les clients de type B

        System.out.println("La somme des temps d'attentes:");
        System.out.println("Wn,a (Clients de type A): " + sumWaitTimesA);
        System.out.println("Wn,b (Clients de type B): " + sumWaitTimesB);
        System.out.println("Estimation des temps d'attente moyens à long terme:");
        System.out.println("wa (Clients de type A): " + wa);
        System.out.println("wb (Clients de type B): " + wb);

    }


    public static void main(String[] args) {

        Bank bank = new Bank();
        bank.simulate(nbDay);

        bank.displayResult();

        SwingUtilities.invokeLater(() -> {
            Histogram custA = new Histogram("Histogramme pour le client de type A", dailyWaitTimesA, "Jour", "Temps d'attente");
            custA.pack();
            custA.setVisible(true);

            Histogram custB = new Histogram("Histogramme pour le client de type B", dailyWaitTimesB, "Jour", "Temps d'attente");
            custB.pack();
            custB.setVisible(true);
        });





    }


}
