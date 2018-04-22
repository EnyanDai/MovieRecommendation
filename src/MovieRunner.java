import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;


/**
 * The MovieRunner can be ran from the commandline to predict user ratings.
 * Example command to run:
 *      java -cp .:bin/ MovieRunner -trainingFile data/ra.train -matrixFile data/ra.matrix -testFile data/ra.test
 *
 * @author Toon Van Craenendonck
 * @author Pieter Robberechts
 */

public class MovieRunner {

    static MovieHandler ratings;
    static PearsonsCorrelation similarities;
    static boolean onlinePearson = false;
    static String testFile;
    static int K = Integer.MAX_VALUE;
    
    /**
     * Method to set how many people at most we used to predict rates
     */
    public static void SetParam(int k) {
    	K = k;
    }
 

    /**
     * Predict the rating of user with external id externUserID for movie with id movieID.
     *
     * @param externUserID external id of user whose rating should be predict
     * @param movieID movie for which the rating should be predicted
     * @return the predicted rating
     */
    public static double predictRating(int externUserID, int movieID){
    	double rating = MovieHandler.DEFAULT_RATING;
    	Set<Neighbor> relations = similarities.getrelations(externUserID);
    	double s = 0.0;
    	double r1 = 0.0;
    	int k = 0;
    	for(Neighbor neighbor:relations) {
    		int ID = neighbor.getUserID();
    		Double TmpRate = IsRated(ID,movieID);
    		if( (!TmpRate.isNaN()) & (k < K) ) {
    			double p = neighbor.getSimilarity();
    			r1 += p*(TmpRate-ratings.getUserAverageRating(ID));
    			s += Math.abs(neighbor.getSimilarity());
    			k++;
    		}
    	}
    	if(s == 0.0) return ratings.getUserAverageRating(externUserID);
    	rating = ratings.getUserAverageRating(externUserID)+r1/s;
        return rating;
    }
    public static Double IsRated(int userID,int movieID) {
    	for(MovieRating rating:ratings.getUsersToRatings().get(userID)) {
    		if(rating.getMovieID()==movieID) {
    			return rating.getRating();
    		}
    	}
    	return Double.NaN;
    }



    /**
     * For each user/movie combination in the test set, predict the users'
     * rating for the movie and compare to the true rating.
     * Prints the current mean absolute error (MAE) after every 50 users.
     *
     * @param testFile path to file containing test set
     */
    public static void evaluate(String testFile) {

        double summedErrorRecommenderSq = 0;
        double summedErrorAvgSq = 0;

        int avg_used = 0;
        int est_used = 0;
        int ctr = 0;

        BufferedReader br;
        int startTime = (int) (System.currentTimeMillis()/1000);
        int elapsedTime = 0;
        try {
            br = new BufferedReader(new FileReader(testFile));
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("::|\t");

                int userID = Integer.parseInt(tokens[0]);
                int movieID = Integer.parseInt(tokens[1]);
                double rating = Double.parseDouble(tokens[2]);

                double avgRating = ratings.getMovieAverageRating(movieID);
                double estimate = predictRating(userID, movieID);

                summedErrorRecommenderSq += Math.pow(rating - estimate,2);
                summedErrorAvgSq += Math.pow(rating - avgRating, 2);
                ctr++;

                if (avgRating == estimate) {
                    avg_used++;
                } else {
                    est_used++;
                }
                if ((ctr % 50) == 0) {
                    elapsedTime = (int)(System.currentTimeMillis()/1000) - startTime;
                    int remainingTime = (int) (elapsedTime * 698780f / ctr) - elapsedTime;
                    System.out.println("RMSE (default): " + Math.sqrt(summedErrorAvgSq/ctr)
                    + " RMSE (recommender): " + Math.sqrt(summedErrorRecommenderSq/ctr)
                    + " Time remaining: " + (int) ((remainingTime / (60*60)) % 24) + "h" + (int) ((remainingTime / 60) % 60)
                    );
                }
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public static void main(String[] args) {

        String trainingFile = "";
        String testFile = "";
        String matrixFile = null;
       
        int i = 0;
        while (i < args.length && args[i].startsWith("-")) {
            String arg = args[i];
            if(arg.equals("-trainingFile")) {
                trainingFile = args[i+1];
            } else if(arg.equals("-testFile")) {
                testFile = args[i+1];
            } else if(arg.equals("-matrixFile")) {
                matrixFile = args[i+1];
            } else if(arg.equals("-onlinePearson")) {
                onlinePearson = true;
            }
            // ADD ADDITIONAL PARAMETERS HERE //
            i += 2;
        }

        ratings = new MovieHandler(trainingFile);
        if (!onlinePearson)
            // Load a precomputed Pearson correlation matrix
            similarities = new PearsonsCorrelation(ratings, matrixFile);
        else
            // Compute Pearson correlations on the fly.
            // Beware that this will be very slow!
            similarities = new PearsonsCorrelation();
        SetParam(200);
        evaluate(testFile);
    }

}
