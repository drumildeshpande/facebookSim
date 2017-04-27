package main

import akka.actor._
import scala.collection.mutable._
import spray.http._
import spray.client.pipelining._
import scala.util.Random
import scala.concurrent.duration._

/**
 * @author Drumil Deshpande
   @author Sumeet Pande
 */
object client 
{
  def main(args: Array[String])
  {
    //Accept input arguements : Number of clients
    if(args(0).toLowerCase == "client" && args.length==2) 
    { 
      println("Client server initiated")
      var num_users = args(1).toInt
      val system = ActorSystem("FacebookSimulator")
      val clientSystem = system.actorOf(Props(new client(num_users,system)),"clientSystem")
      clientSystem ! "Initiate"
    }
  }


class client(num_users:Int, actSys:ActorSystem) extends Actor{
  
  import actSys.dispatcher
  val pipeline = sendReceive
  val pipeline2 = sendReceive
  val start_time:Long=System.currentTimeMillis
  var time:Int = 10000
  
  def receive = {
    
    case "Initiate" => {      
      
      //Initiating facebook users.
      for (i<-0 to num_users-1)
      {
          val users=actSys.actorOf(Props(new user(num_users,i,actSys)),name="user_"+i.toString())
      }
      //Sending the "POST" request to webserver to initiate users 
      println("Initialized " + num_users + " facebook users")      
      val result = pipeline(Post("http://localhost:8080/facebook/initiate?num_users="+num_users))
      println(num_users + " Facebook Users Created")
      
      //Timeout require for the server to create all data for each users.
      Thread.sleep(time)
      self ! "startFacebookScheduling"      
    }
    
    case "startFacebookScheduling"=> {
      val sch=context.system.scheduler.schedule(0 seconds, 2 seconds,self,"startFacebookActivity")
    }
    
    case "startFacebookActivity" => {
      
      // Users are randomly selected to perform activities such as getProfile, viewFriends etc.
      println("Start facebook api activity")
      var randUser1 = context.actorSelection("akka://FacebookSimulator/user/user_"+Random.nextInt(num_users).toString)
      randUser1 !"getProfile"
      randUser1! "returnFriends"
      randUser1! "viewPage"
      randUser1 ! "viewAlbum"
      randUser1 ! "addFriend"
      randUser1 ! "postImage"
      randUser1 ! "postNewMsg"
      
      
      var randUser2 = context.actorSelection("akka://FacebookSimulator/user/user_"+Random.nextInt(num_users).toString)
      randUser2 ! "postNewMsg" 
      randUser2! "returnFriends"
      randUser2! "postImage"
      randUser2 ! "getProfile"
      randUser2 ! "viewPage"
      randUser2 ! "viewAlbum"
      randUser2 ! "addFriend"
      
      var randUser3 = context.actorSelection("akka://FacebookSimulator/user/user_"+Random.nextInt(num_users).toString)
      randUser3 ! "postNewMsg" 
      randUser3! "returnFriends"
      randUser3! "postImage"
      randUser3 ! "getProfile"
      randUser3 ! "viewPage"
      randUser3 ! "viewAlbum"
      randUser3 ! "addFriend"
      
      // The client shuts down after a certain amount of time to terminate the simualtion
      if(System.currentTimeMillis - start_time > (time + 10000))
      {
        println("Shutting down .....")
        //context.system.shutdown()
        actSys.shutdown
      }
      
    }    
  }
 }
}