package llr

import java.util.Random

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/** Case class that defines all the Configurations
  *
  * @param master the User from the interaction input file
  *
  *
  */


case class Config(master: String = "master",
                  options: String = "options",
                  useroritem: String = "-u",
                  threshold: Double = 0.6,
                  interactionsFile:String = "/Users/Dungeoun/Documents/SkymindLabsInternship/raghu/Like2Vec/llr/src/main/resources/sampleinput/example1.txt",
                  separator:String = ",",
                  numSlices:Int = 2,
                  maxSimilarItemsperItem:Int = 100,
                  maxInteractionsPerUserOrItem:Int = 500,
                  seed:Int = 12345
                 )



/** Case class that defines an Interaction between a User and an Item to be used in log-likelihood
  * algorithm.
  *
  * @param user the User from the interaction input file
  * @param item the Item from the interaction input file
  *
  */

case class Interaction(val user: String, val item: String)

object LLR {

  /** Spark Application used as the first step in Like2Vec for generating the Log Likelihood Ratio for different
    * pairs of Users and sets of Items.
    *
    * Requires passing the output option as first argument and location of input file
    * that contains the relevant data as the second:
    *
    *    1. Different output options for configurations:
    *         -d for Default
    *         -m for MaxSale
    *         -c for Continuous Ratio
    *
    *    2. The Path for the Dataset of the form (User, Item)
    *
    */




  val hdfs ="hdfs://ip-172-31-23-118.us-west-2.compute.internal:8020"
    // val inputFile =hdfs+"/user/hadoop/trainset"
  def main(args: Array[String]) {
    userSimilarites(
      "master",
       args(0),
       args(1),
       args(2).toDouble,
       args(3),
       ",",
       2,
       100,
       500,
       12345)
  }

  /** Finds out the similarities amongst Users by first reading the data from the
    * User-Item interactions input file and then calls the loglikelihood method
    * to implement the algorithm.
    *
    * @param master ? Not Used
    * @param options Output options for LLR namely Default -d, MaxSale -m, Continuous Ratio -c
    * @param useroritem Input option for LLR to find User-User -u or Item-Item -i Similarity
    * @param interactionsFile Input file containing user,items data
    * @param separator Delimiter used for separating user and item (eg. "," or "::")
    * @param numSlices ? Not Used
    * @param maxSimilarItemsperItem ? Not Used
    * @param maxInteractionsPerUserOrItem Specifies maximum number of Interactions that can be taken
    * @param seed Specifies Seed parameter of Hash Function used for randomization
    */
   
  def userSimilarites(
    master:String,
    options:String,
    useroritem: String,
    threshold: Double,
    interactionsFile:String,
    separator:String,
    numSlices:Int,
    maxSimilarItemsperItem:Int,
    maxInteractionsPerUserOrItem:Int,
    seed:Int){
   // System.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
  //  System.setProperty("spark.kryo.registrator", classOf[CoocRegistrator].getName)
 //  System.setProperty("spark.kryo.referenceTracking", "false")
//   System.setProperty("spark.kryoserializer.buffer.mb", "8")
    System.setProperty("spark.locality.wait", "10000")
    val sc = new SparkContext("local","CooccurrenceAnalysis");

//  val sc = new SparkContext(new SparkConf().setAppName("CooccurrenceAnalysis"));

    /** Reading Data from input file,RDD */

    val rawInteractions = sc.textFile(interactionsFile)
//    val header =rawInteractions.first()
//    val rawInteractions_data = rawInteractions.filter(row => row != header)


    val rawInteractions_set =
      if (useroritem == "-u")
        rawInteractions.map { line =>
          val fields = line.split(separator)
          Interaction(fields(0), fields(1))
        }
      else
        rawInteractions.map { line =>
          val fields = line.split(separator)
          Interaction(fields(1), fields(0))
        }


    /** interactions holds Number of Interactions of each item per user,
      * or Number of Interactions of each user per item
      *
      */

    val interactions =
      downSample(
        sc,
        rawInteractions_set,
        maxInteractionsPerUserOrItem,
        seed)
    interactions.cache()

    val numInteractions = interactions.count()
    val numInteractionsPerItem =
      countsToDict(interactions.map(interaction => (interaction.user, 1))
        .reduceByKey(_ + _))
    sc.broadcast(numInteractionsPerItem)
    val numItems =
      countsToDict(interactions.map(interaction => (interaction.item, 1))
        .reduceByKey(_ + _))




      val cooccurrences = interactions.groupBy(_.item)
        .flatMap({ case (user, history) => {
          for (interactionA <- history; interactionB <- history)
            yield { ((interactionA.user, interactionB.user), 1l)
            }
        }
        }).reduceByKey(_ + _)



      /** Determining the values of interaction of user/item A with user/item B.
        * Calls the loglikelihoodRatio method by using the above mentioned values to calculate
        * logLikelihood Similarity metric.
        *
        */

      val similarities = cooccurrences.map{ case ((userA, userB), count) =>
        val interactionsWithAandB = count
        val interactionsWithAnotB =
          numInteractionsPerItem(userA) - interactionsWithAandB
        val interactionsWithBnotA =
          numInteractionsPerItem(userB) - interactionsWithAandB
        val interactionsWithNeitherAnorB =
          (numItems.size) - numInteractionsPerItem(userA) -
            numInteractionsPerItem(userB) + interactionsWithAandB
        val logLikelihood =
          LogLikelihood.logLikelihoodRatio(
            interactionsWithAandB,
            interactionsWithAnotB,
            interactionsWithBnotA,
            interactionsWithNeitherAnorB)
        val logLikelihoodSimilarity = 1.0 - 1.0 / (1.0 + logLikelihood)
        //((userA, userB), logLikelihoodSimilarity)

        /** Calculating Row Entropy and Column Entropy to give out different output options for
          * different configurations:
          *
          * -d for Default
          * -m for MaxSale
          * -c for Continuous Ratio
          *
          */

        val rEntropy: Double = LogLikelihood.entropy(
          interactionsWithAandB+interactionsWithAnotB,interactionsWithBnotA+interactionsWithNeitherAnorB)
        val cEntropy: Double = LogLikelihood.entropy(
          interactionsWithAandB+interactionsWithBnotA,interactionsWithAnotB+interactionsWithNeitherAnorB)

        val llrD: Double = logLikelihoodSimilarity
        val llrM: Double = logLikelihoodSimilarity /(2 * math.max(rEntropy, cEntropy))
        val llrC: Double = logLikelihoodSimilarity / (1 + logLikelihoodSimilarity)

//        if (options == "-d" && llrD > threshold){
        ((userA.toDouble, userB.toDouble), llrD, llrM, llrC)
//        }
//        else if (options == "-m" && llrM > threshold) {
          //val outM = ((userA.toDouble, userB.toDouble), llrM)
//        }
//        else if (options == "-c" && llrC > threshold) {
          //val outC((userA.toDouble, userB.toDouble), llrC)
//        }

      }

    val llrD1 = similarities.map(x=>x._2)
    val llrM1 = similarities.map(x=>x._3)
    val llrC1 = similarities.map(x=>x._4)

    if (options == "-d") {
      similarities.map(x=>((x._1),x._2)).filter(f=>f._2 > threshold).repartition(1).saveAsTextFile("./src/main/resources/CellDataOP");
    }
    else if(options == "-m"){
      similarities.map(x=>((x._1),x._3)).filter(f=>f._2 > threshold).repartition(1).saveAsTextFile("./src/main/resources/CellDataOP");
    }
    else if (options == "-c"){
      similarities.map(x=>((x._1),x._4)).filter(f=>f._2 > threshold).repartition(1).saveAsTextFile("./src/main/resources/CellDataOP");
    }


      //output.saveAsTextFile("./src/main/resources/CellDataOP");

      // similarities.repartition(1).saveAsTextFile(hdfs+"/user/hadoop/LlrTrainSet");

    //if(similarities.isEmpty()==false) {
      //similarities.repartition(1).saveAsTextFile("./src/main/resources/CellDataOP");
      //sc.stop()
    //}
    }



  /** Calculates the number of interactions of each user with every possible item
    * and the number of interactions of each item with every possible user by calling the countsToDict
    * method.
    *
    * @param sc Spark Context
    * @param interactions Contains a user and item pair
    * @param maxInteractionsPerUserOrItem Specifies maximum number of Interactions that can be taken
    * @param seed Specifies Seed parameter of Hash Function used for randomization
    * @return Number of Interactions of each item per user, or Number of Interactions of each user per item
    */

  def downSample(
    sc:SparkContext,
    interactions: RDD[Interaction], 
    maxInteractionsPerUserOrItem: Int, 
    seed: Int) = {
    val numInteractionsPerUser = 
      countsToDict(interactions.map(interaction => (interaction.user, 1)).
        reduceByKey(_ + _))
    sc.broadcast(numInteractionsPerUser)
    val numInteractionsPerItem =
      countsToDict(interactions.map(interaction => (interaction.item, 1)).
        reduceByKey(_ + _))
    sc.broadcast(numInteractionsPerItem)

    /** Implements a hash function to generate a random number
      *
      * @param x Seed value
      * @return
      */

    def hash(x: Int): Int = {
      val r = x ^ (x >>> 20) ^ (x >>> 12)
      r ^ (r >>> 7) ^ (r >>> 4)
    }

    /** apply the filtering on a per-partition basis to ensure repeatability in case of failures by
       incorporating the partition index into the random seed */

    interactions.mapPartitionsWithIndex({ case (index, interactions) => {
      val random = new Random(hash(seed ^ index))
      interactions.filter({ interaction => {
        val perUserSampleRate = math.min(
          maxInteractionsPerUserOrItem, numInteractionsPerUser(interaction.user)) / 
          numInteractionsPerUser(interaction.user)
        val perItemSampleRate = math.min(
          maxInteractionsPerUserOrItem, numInteractionsPerItem(interaction.item)) / 
          numInteractionsPerItem(interaction.item)
        random.nextDouble() <= math.min(perUserSampleRate, perItemSampleRate)
      }
    })
  }
})
}

  /** Counts the number of Interactions per user, and Interactions per item.
    *
    * @param tuples Key Value pair where key is either user or item and the value is its count
    *
    */

  def countsToDict(tuples: RDD[(String, Int)]) = {
    tuples.collect().foldLeft(Map[String, Int]()) {
      case (table, (item, count)) => table + (item -> count) 
    }
  }

}

object LogLikelihood {

  /** Determines Log Likelihood Ratio by calculating row entropy, column entropy and matrix entropy using
    * following parameters.
    *
    * @param k11 Interactions of items With User A and User B, or users with Item A and Item B
    * @param k12 Interactions of items With User A and not User B, or users with Item A and not Item B
    * @param k21 Interactions of items With User B and not User A, or users with Item B and not Item A
    * @param k22 Interactions of items With neither User B nor User A, or users with neither Item B nor Item A
    * @return
    *
    */

  def logLikelihoodRatio(k11: Long, k12: Long, k21: Long, k22: Long) = {
    val rowEntropy: Double = entropy(k11 + k12, k21 + k22)
    val columnEntropy: Double = entropy(k11 + k21, k12 + k22)
    val matrixEntropy: Double = entropy(k11, k12, k21, k22)
    if (rowEntropy + columnEntropy < matrixEntropy) {
      0.0
    } else {
      2.0 * (rowEntropy + columnEntropy - matrixEntropy)
    }
  }


  /** Calculates x*log(x) expression
    *
    * @param x Any input of long datatype
    * @return
    */

  private def xLogX(x: Long): Double = {
    if (x == 0) {
      0.0
    } else {
      x * math.log(x)
     
    }
  }


  /**
    * Merely an optimization for the common two argument case of {@link #entropy(a: Long, b: Long)}
    * @see #logLikelihoodRatio(long, long, long, long)
    */

  private def entropy(a: Long, b: Long): Double = { xLogX(a + b) - xLogX(a) - xLogX(b) }

  /**
    * Calculates the unnormalized Shannon entropy.  This is
    *
    * -sum x_i log x_i / N = -N sum x_i/N log x_i/N
    *
    * where N = sum x_i
    *
    * If the x's sum to 1, then this is the same as the normal
    * expression.  Leaving this un-normalized makes working with
    * counts and computing the LLR easier.
    *
    * @return The entropy value for the elements
    */

  def entropy(elements: Long*): Double = {
    var sum: Long = 0
    var result: Double = 0.0
    for (element <- elements) {
      result += xLogX(element)
      sum += element
    }
    xLogX(sum) - result
  }
}