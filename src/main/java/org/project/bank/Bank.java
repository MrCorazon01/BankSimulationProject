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
    int nbDay;

    //Variables génératives
    ExponentialGen[] genArrivalA;
    RandomVariateGen genServiceA;
    RandomVariateGen genServiceB;
    RandomVariateGen genDelayB;

    // Listes

    LinkedList<Customer> waitListA = new LinkedList<> ();
    LinkedList<Customer> waitListB = new LinkedList<> ();

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

    public static class Cashier {
        // Class pour pouvoir avoir le nombre de cashiers
    }

    // Dans la classe Advisor
    public class Advisor {
        double busyUntil = 0.0; // Heure à laquelle le conseiller sera libre
        boolean hasAppointment = false; // Indique si le conseiller a un rendez-vous en cours
        List<Appointment> appointments = new ArrayList<>(); // Liste des rendez-vous du conseiller

        public boolean canServe(double currentTime) {
            return currentTime >= busyUntil; // Le conseiller est disponible s'il n'est pas occupé
        }



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
        public void finishAppointment() {
            hasAppointment = false;
            busyUntil = Sim.time(); // Le conseiller est libre dès qu'il a fini de servir
        }

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

    private void generateAppointmentsForTypeB(){
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


    public Bank(int[] numCashiers, int[] numAdvisors, double[] arrivalRates,
                double tA_mean, double tA_std, double r_mean, double r_std,
                double tB_mean, double tB_std, double r_rate, double p_absent,
                double s, int nbDay){

        NUM_CASHIERS = numCashiers;
        NUM_ADVISORS = numAdvisors;
        ARRIVAL_RATES = arrivalRates;
        this.tA_mean = tA_mean;
        this.tA_std = tA_std;
        this.r_mean = r_mean;
        this.r_std = r_std;
        this.tB_mean = tB_mean;
        this.tB_std = tB_std;
        this.r_rate = r_rate;
        this.p_absent = p_absent;
        this.s = s;
        this.nbDay = nbDay;

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


        generateAppointmentsForTypeB();

        // Planifier le premier événement de départ des clients de type B après l'ouverture de la banque
        for (Customer custB : waitListB) {
            double actualAppointmentTime = custB.appointment.appointmentTime;
            new DepartureTypeB().schedule(actualAppointmentTime + custB.servTime);
        }

        Sim.start();
    }

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





    class EndOfSim extends Event {
        public void actions() {
            if (Sim.time() >= CLOSING_TIME) {

                Sim.stop(); // Arrêter la simulation lorsque tous les clients ont été servis
            }
        }
    }


    // Méthode pour afficher les résultats de la simulation
    public void displayResults() {
        double avgWaitTimeA = totWaitTimeA / numClientsA;
        double avgWaitTimeB = totWaitTimeB / numClientsB;

        System.out.println("Temps d'attente moyen des clients de type A : " + avgWaitTimeA);
        System.out.println("Temps d'attente moyen des clients de type B : " + avgWaitTimeB);

    }



    public static void main(String[] args) {
        // Définir les valeurs spécifiées
        int[] numCashiers = { 3, 4, 3 };
        int[] numAdvisors = { 2, 3, 3 };
        double[] arrivalRates = { 20.0 / HOUR, 35.0 / HOUR, 28.0 / HOUR };
        double tA_mean = 200.0;
        double tA_std = 60.0;
        double r_mean = 100.0;
        double r_std = 90.0;
        double tB_mean = 20.0 * 60.0;
        double tB_std = 8.0 * 60.0;
        double r_rate = 0.8;
        double p_absent = 0.05;
        double s = 10.0 * 60.0;
        int nbDay = 2;

        Bank bank = new Bank(numCashiers, numAdvisors, arrivalRates, tA_mean, tA_std, r_mean, r_std,
                tB_mean, tB_std, r_rate, p_absent, s, nbDay);

        bank.simulate(nbDay); //Nombre de jours



        bank.displayResults();

    }


}
