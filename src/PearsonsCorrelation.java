
import java.util.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * Computes a matrix with Pearson's product-moment correlation coefficients
 * for the ratings given to movies by pairs users.
 *
 * Correlations are given by the formula:
 *      cor(X, Y) = Σ[(xi - E(X))(yi - E(Y))] / [(n - 1)s(X)s(Y)]
 * where E(X) is the mean of X, E(Y) is the mean of the Y values and s(X),
 * s(Y) are standard deviations.
 *
 * The PearsonsCorrelation can be ran from the commandline to construct
 * the matrix and to save the result to a file afterwards.
 * Example command:
 *      java -cp .:bin/ PearsonsCorrelation -trainingFile data/r1.train -outputFile out/r1.matrix
 *
 * @author Pieter Robberechts
 *
 */
public class PearsonsCorrelation {


    /**
     * Create an empty PearsonsCorrelation instance with default parameters.
     */
	private Map<Integer,relationlist> Matirx = new HashMap<Integer,relationlist>();
	private Map<Integer, List<MovieRating>> usersToRatings;
	private List<Integer> userIDs;
	private Map<Integer,Integer> index = new HashMap<Integer,Integer>();
	private Tasks tasks;
	private int param1 = 125;
	private int K = 200;
	private int numberOfThreads = 4;
	
	/**
	 * 
	 * @author Enyan
	 *A class to encapsulate the PearsonsCorrelation list every ID correspond to
	 *TreeSet is used to record the neighbor ordered.
	 */
	private class relationlist{
		private TreeSet<Neighbor> relations = new TreeSet<Neighbor>(new Comparator<Neighbor>() {
			/**
			 * a > b if |a.similarity| > |b.similarity| 
			 */
			public int compare(Neighbor a, Neighbor b) {
                if(Math.abs(a.similarity)>Math.abs(b.similarity)) {
                	return 1;
                }
                else if(Math.abs(a.similarity) == Math.abs(b.similarity)) {
                	if(a.id > b.id)return 1;
                	else if(a.id == b.id) return 0;
                	return -1; 
                }
                return -1;
            }
		}.reversed());
		
		private double getvalue(int id) {
			for(Neighbor neighbor:relations) {
				if(neighbor.getUserID() == id) {
					return neighbor.getSimilarity();
				}
			}
			return (Double) null;
		}
		
		
		private void add(int id,double value,int limit) {
			if(this.relations.size() < limit) {
				this.relations.add(new Neighbor(id,value));
			}
			else if(Math.abs(this.relations.last().getSimilarity()) < Math.abs(value)){
				this.relations.pollLast();
				this.relations.add(new Neighbor(id,value));
			}
		}
		
		private void add(int id,double value) {
			this.relations.add(new Neighbor(id,value));
		}
		/**
		 * For debug 
		 */
		public void printlist() {
			for(Neighbor neighbor:this.relations) {
				System.out.print(neighbor+" , ");
			}
			System.out.println();
		}
		/**
		 * change the sparse list into a String array to print
		 */
		public String[] getString() {
			String[] s = new String[userIDs.size()];
			for(int i=0;i<userIDs.size();i++) {
				s[i]="NaN";
			}
			for(Neighbor neighbor:relations) {
				s[index.get(neighbor.getUserID())]=String.format("%.4f", neighbor.getSimilarity());
			}
			return s;
		}
	}
    public PearsonsCorrelation() {
        super();
        // FILL IN HERE //
    }

    /**
     * Create a PearsonsCorrelation instance with default parameters.
     */
    public PearsonsCorrelation(MovieHandler ratings) {
        super();
        // FILL IN HERE //
        this.usersToRatings = ratings.getUsersToRatings();
        this.userIDs = ratings.getUserIDs();
        for(int i = 0;i<userIDs.size();i++) {
        	this.index.put(userIDs.get(i), i);
        }
        this.tasks = new Tasks(this.userIDs.size());
        System.out.println("Writing Matrix.. ");
        createMatrix();
        System.out.println("-----------Finished---------- ");
        
    }
    /**
     * 
     * The task mark when we using multithreads to create matrix 
     *
     */
    private class Tasks{
    	int number = 0;
    	Tasks(int size){
    		number = size;
    	}
    	public synchronized int nextTask() {
    		number--;
    		return number;
    	}
    }
    /**
     *A private class to do parallel calculation 
     */
    private class MultiThreads implements Runnable{
    	private Thread t;
		@Override
		public void run() {
			int i = userIDs.size()-tasks.nextTask()-1;
			while(i<userIDs.size()) {
				int id1 = userIDs.get(i);
				// TODO Auto-generated method stub
				for(int j = 0 ;j < userIDs.size();j++) {
    				int id2 = userIDs.get(j);
    				if(id1!=id2) {
	    				double corr = correlation(usersToRatings.get(id1), usersToRatings.get(id2));
	    				if(corr!=0.0) {
	    					Matirx.get(id1).add(id2,corr,K);
	    				}
    				}
    			}
				int size = Matirx.containsKey(id1)?Matirx.get(id1).relations.size():0;
				if(i%100==0) System.out.println("we are in "+ i +" size " +size);
				if(i%1000==0)System.gc();
//				System.out.println("we are in "+ i +" size " +size);
//				Matirx.get(id1).printlist();
				i = userIDs.size()-tasks.nextTask()-1;
			}
		}
		public void start() {
			t = new Thread(this);
			t.start();
		}
		public void join() {
			try {
				t.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	
    }
    /**
     * a method to create PearsonsCorrelation matrix 
     * the matrix is asymmetric
     */
    public void createMatrix() {
    	for(int i =0; i < this.userIDs.size();i++) {
    		int id1 = this.userIDs.get(i);
    		if(!Matirx.containsKey(id1)) {
				Matirx.put(id1, new relationlist());
			}
    	}
    	MultiThreads[] multithreads = new MultiThreads[numberOfThreads];
    	for(int i = 0;i<multithreads.length;i++) {
    		multithreads[i] = new MultiThreads();
    		multithreads[i].start();
    	}
    	for(MultiThreads thread:multithreads) {
    		thread.join();
    	}
    }
    



    /**
     * Load a previously computed PearsonsCorrelation instance.
     */
    public PearsonsCorrelation(MovieHandler ratings, String filename) {
        // FILL IN HERE //
    	this.usersToRatings = ratings.getUsersToRatings();
        this.userIDs = ratings.getUserIDs();
        try {
        	long startTime = System.currentTimeMillis();
            System.out.println("Reading Matrix.. ");
            readCorrelationMatrix(filename);
            System.out.println("done, took " +  (System.currentTimeMillis() - startTime)/1000.0 + "seconds.");
            System.out.println("--------------");
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }


    /**
     * Returns the correlation between two users.
     *
     * @param i True user id
     * @param j True user id
     * @return The Pearson correlation
     */
    public double get(int i, int j) {
        double correlation = 0;
        // FILL IN HERE //
        if(!this.Matirx.containsKey(i)) return (Double)null;
        correlation = this.Matirx.get(i).getvalue(j);
        return correlation;
    }
    public TreeSet<Neighbor> getrelations(int userID) {
    	return this.Matirx.get(userID).relations;
    }



    /**
     * Computes the Pearson's product-moment correlation coefficient between
     * the ratings of two users.
     *
     * Returns {@code NaN} if the correlation coefficient is not defined.
     *
     * @param xArray first data array
     * @param yArray second data array
     * @return Returns Pearson's correlation coefficient for the two arrays
     */
    public double correlation(List<MovieRating> xRatings, List<MovieRating> yRatings) {
        double correlation = 0;
        // FILL IN HERE //
        
//        List<Double> Ratings1 = new ArrayList<Double>();
//        List<Double> Ratings2 = new ArrayList<Double>();
        double average1 = 0;
        double average2 = 0;
        BitSet xSet = new BitSet();
        BitSet ySet = new BitSet();
        for(MovieRating xRating: xRatings) {
        	xSet.set(xRating.getMovieID());
        }
        for(MovieRating yRating: yRatings) {
        	ySet.set(yRating.getMovieID());
        }
        xSet.and(ySet);
        int size = xSet.cardinality();
        if(size == 0) {
        	return 0;
        }
        
        double w = 1.0;
        if(size < this.param1) {
        	w = (double)size/(double)this.param1;
        }
        double[] xlist = new double[size];
        double[] ylist = new double[size];
        int n = 0;
        for(MovieRating xRating:xRatings) {
        	if(xSet.get(xRating.getMovieID())) {
        		xlist[n] = xRating.getRating();
        		average1 = average1 + xRating.getRating();
        		n++;
        	}
        }
        n = 0;
        for(MovieRating yRating: yRatings) {
    		if(xSet.get(yRating.getMovieID())) {
    			ylist[n] = yRating.getRating();
    			average2 = average2 +yRating.getRating();
    			n++;
    		}
    	}
      
        average1 = average1/(double)size;
        average2 = average2/(double)size;
        double s1 = 0;
        double s2 = 0;
        for(int i = 0 ; i < size ;i++) {
        	double x = xlist[i];
        	double y = ylist[i];
        	correlation += (x-average1)*(y-average2);
        	s1 = s1+(x-average1)*(x-average1);
        	s2 = s2+(y-average2)*(y-average2);
        }
        if(s1==0 || s2==0) return 0.0;
        correlation = w*correlation/(Math.sqrt(s1)*Math.sqrt(s2));
        return correlation;
    }


    /**
     * Writes the correlation matrix into a file as comma-separated values.
     *
     * The resulting file contains the full nb_users x nb_users correlation
     * matrix, such that the value on position (row_i, col_j) corresponds to
     * the correlation between the user with internal id i and the user with
     * internal id j. The values are separated by commas and rounded to four
     * decimal digits. The actual matrix starts on line 3. The first line
     * contains a single integer which defines the size of the matrix. The
     * second line is reserved for additional parameter values which where
     * used during the construction of the correlation matrix. You are free to
     * use any format for this line. E.g.:
     *  3
     *  param1=value,param2=value
     *  1.0000,-.3650,NaN
     *  -.3650,1.0000,.0012
     *  NaN,.0012,1.0000
     *
     * @param filename Path to the output file.
     * @throws Exception 
     * @throws IOException 
     */
    public void writeCorrelationMatrix(String filename) throws Exception{
        // FILL IN HERE //
    	File f = new File(filename);
    	File parent = f.getParentFile();
    	if(!parent.exists()) {
    		parent.mkdirs();
    	}
		FileOutputStream fop = new FileOutputStream(filename);
		OutputStreamWriter writer = new OutputStreamWriter(fop,"UTF-8");
		writer.write(String.valueOf(this.Matirx.size())+"\n");
		String param ="param1=10,param2=0.4,param3=200\n";
		writer.write(param);
		for(int k = 0;k<this.userIDs.size();k++) {
			int ID = this.userIDs.get(k);
			String[] line = this.Matirx.get(ID).getString();
			for(int i = 0;i < line.length-1;i++) {
				writer.write(line[i]+",");
			}
			writer.write(line[line.length-1]+"\n");
			if(k%1000==0) {
				System.out.println("We are wrting line "+k);
				System.gc();
			}
		}
		writer.close();
    }
    


    /**
     * Reads the correlation matrix from a file.
     *
     * @param filename Path to the input file.
     * @throws FileNotFoundException 
     * @see writeCorrelationMatrix
     */
    public void readCorrelationMatrix(String filename) throws Exception {
        // FILL IN HERE //
    	this.Matirx.clear();
    	BufferedReader reader = new BufferedReader(new FileReader(filename));
    	int size = Integer.parseInt(reader.readLine());
    	reader.readLine();
    	for(int i=0;i<size;i++) {
    		String relationstr = reader.readLine();
    		String[] rela = relationstr.split(",");
    		relationlist relations = new relationlist();
    		for(int j = 0;j<rela.length;j++) {
    			if(!rela[j].equals("NaN")) {
    				relations.add(this.userIDs.get(j), Double.parseDouble(rela[j]));
    			}
    		}
//    		System.out.println("we are in "+ i +" size " +size);
//    		relations.printlist();
    		
    		
    		this.Matirx.put(this.userIDs.get(i), relations);
    		if(i%1000==0) System.gc();
    	}
    }


    public static void main(String[] args) {
        String trainingFile = "";
        String outputFile = "";

        int i = 0;
        while (i < args.length && args[i].startsWith("-")) {
            String arg = args[i];
            if(arg.equals("-trainingFile")) {
                trainingFile = args[i+1];
            } else if(arg.equals("-outputFile")) {
                outputFile = args[i+1];
            } 
            // ADD ADDITIONAL PARAMETERS //
            i += 2;
        }

        MovieHandler ratings = new MovieHandler(trainingFile);
        PearsonsCorrelation matrix = new PearsonsCorrelation(ratings);
        try {
			matrix.writeCorrelationMatrix(outputFile);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

}
