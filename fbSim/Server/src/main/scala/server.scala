package main

import akka.actor._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import util.Random
import scala.util.Random
import scala.concurrent.duration._
//import global.dataPool
//import main.dataPool
import akka.routing.RoundRobinRouter
import spray.routing.SimpleRoutingApp
import spray.json.AdditionalFormats
import spray.routing._
import spray.routing.Directive._
import spray.http._
import java.io
import shapeless.get
import akka.pattern.AskTimeoutException
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future._
import spray.httpx._
import scala.concurrent.Future
import scala.concurrent.Await
import akka.util.Timeout
import org.json4s.ShortTypeHints
import org.json4s.native.Serialization
import org.json4s.native.Serialization._
import scala.collection.mutable.SynchronizedBuffer


case class buildInitialData(noUsers:Int)
case class postMsg(user_id:Int, post:String)
case class returnFriends(userId:Int)
case class postImg(user_id:Int, albumid:Int , post:String)
case class fetchProfile(user_id:Int)
case class fetchPage(user_id:Int, page_title:String)
case class fetchAlbum (user_id:Int, album_id:Int)
case class addFriend(user_Id:Int,friend_ID :Int)
/**
 * @author Drumil Deshpande
   @author Sumeet Pande
 */

object server extends App with SimpleRoutingApp{
  
  override def main(args:Array[String])
  {
    var num_users:Int = 0
    implicit val timeout = Timeout(2.seconds)
    implicit val system = ActorSystem("FacebookServer")
    println("At Facebook WebServer ...")
    val server = system.actorOf(Props(new server(num_users)),"apiServer")
    
    // Facebook Server
    startServer(interface="localhost", port=8080){
     get {
      path("facebook"/"returnFriends") {    /* Get derivative to return friends List */
        parameter("userid") { (userId) =>                      
            complete { 
              (server ? returnFriends(userId.toInt)).recover{
                 case ex: AskTimeoutException => {
                "Problem in Retrieving Post"
                }
                 
             }
             .mapTo[String]
             .map(s => s"Your FriendList is for user $s")
            }              
          }
        }
      }~
      get {
      path("facebook"/"getProfile") {  /* Get derivative to return Profile */
        parameter("userid") { (userId) =>                      
            complete { 
              (server ? fetchProfile(userId.toInt)).recover{
                 case ex: AskTimeoutException => {
                "Problem in fetching Pofile"
                }
                 
             }
             .mapTo[String]
             .map(s => s"Profile is : $s")
            }              
          }
        }
      }~
      get {
      path("facebook"/"page") {  /* Get derivative to return Page */
        parameter("userid" , "pageTitle") { (userId,page_title) =>                      
            complete { 
              (server ? fetchPage(userId.toInt,page_title.toString)).recover{
                 case ex: AskTimeoutException => {
                "Problem in fetching Page"
                }
                 
             }
             .mapTo[String]
             .map(s => s"Page is : $s")
            }              
          }
        }
      }~
      get {
      path("facebook"/"getAlbum") {  /* Get derivative to return Album of photos */
        parameter("userid" , "albumID") { (userId,albumId) =>                      
            complete { 
              (server ? fetchAlbum(userId.toInt,albumId.toInt)).recover{
                 case ex: AskTimeoutException => {
                "Problem in fetching Album"
                }
                 
             }
             .mapTo[String]
             .map(s => s"Page is : $s")
            }              
          }
        }
      }~
      get {
      path("facebook"/"addNewFriend") {  /* Get derivative to Add a friend */
        parameter("userid" , "friendID") { (userId,friendID) =>                      
            complete { 
              (server ? addFriend(userId.toInt,friendID.toInt)).recover{
                 case ex: AskTimeoutException => {
                "Problem in adding new friend"
                }
                 
             }
             .mapTo[String]
             .map(s => s"Updated Friend list is : $s")
            }              
          }
        }
      }~
      post{
       path("facebook"/"initiate"){  /* Post derivative to initiate the data on server for all users */
         parameters("num_users") { (num_users) =>
           complete {
             (server ? buildInitialData(num_users.toInt)).recover
             {
               case ex: AskTimeoutException => 
                 {
                  "Posting Failed"
                }  
             }
             .mapTo[String]
           }
         }
       }
      }~ 
      post {
       path("facebook"/"facebookPost"){  /* Post derivative to Post a message / status */
         parameters("userid".as[Int], "fbPost".as[String]) { (user_id,post) =>
          complete {
            (server?postMsg(user_id,post)).recover{
              case ex: AskTimeoutException => {
                  "Posting Failed"
                }
            }
           .mapTo[String]
           }
         }
       }
     }~
       post {
       path("facebook"/"imagePost"){  /* Post derivative to post an Image */
         parameters("userid".as[Int],"albumID".as[Int],"imagePost".as[String]) { (user_id,album_id,imgPost) =>
          complete {
            (server?postImg(user_id,album_id,imgPost)).recover{
              case ex: AskTimeoutException => {
                  "Image Posting Failed"
                }
            }
           .mapTo[String]
           }
         }
       }
     }
    }
  }
}

/* The class that contains all the REST API methods */

class server(no_users:Int/*, actSys:ActorSystem*/) extends Actor{
  
  /* A few data structures to store the data for all the users on server 
   * userDataArray is list of object userData: contains basic information and friends of all the users
   * postTable contains posts for all the users
   * pageLiked contains all the pages liked by each user
   * num_pages_likes contains the list of users that have liked for each page
   * pages is list of object pageData contains all pages
   * */
  
  
  var num_friends = 0
  var userDataArray = new ArrayBuffer[userData] ()
  val client = context.actorSelection("akka://FacebookSimulator/user/clientSystem")
  val postTable = new ArrayBuffer[ArrayBuffer[String]] ()
  val imageTable=new ArrayBuffer[ArrayBuffer[ArrayBuffer[String]]]()
  val pageLiked = new ArrayBuffer[ArrayBuffer[Int]] ()
  val pages = new ArrayBuffer[pageData]()
  val num_page_likes =  new ArrayBuffer[ArrayBuffer[Int]] ()
  val userid:Int=0
  var num_users:Int = 0
 
  private implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[String])))
  
  def receive = {
    
    /* This case is called to build the initial data for all users, on the server */
    case buildInitialData(noUsers:Int) => {
      
      println("In Build Data func")
      num_users = noUsers
      
      // The number of users depending upon how active they are on facebook
      var high_users = (num_users * 0.7).toInt
      var mid_users  = (num_users * 0.2).toInt
      var low_users  = (num_users * 0.1).toInt
      
      // Pages are created using data in dataPool
      for(i<-0 to 4)
      {
        var likes = ArrayBuffer[Int] ()
        var page = new pageData(dataPool.page_titles(i),dataPool.page_info(i))
        pages += page
        num_page_likes += likes
      }
      
      // Data for each user is created here
      //println("num_users: "+num_users+", "+noUsers)
      for(i<-0 to num_users-1)
      {
         var friendList = new ArrayBuffer[Int] ()
         //println(dataPool.name.length+", "+dataPool.dob.length+", "+dataPool.placeOfBirth.length)
         var randName = dataPool.name(Random.nextInt(dataPool.name.length))
         var randDob = dataPool.dob(Random.nextInt(dataPool.dob.length))
         var randPlace = dataPool.placeOfBirth(Random.nextInt(dataPool.placeOfBirth.length))
         
         // Number of friends are selected depending upon how active the user is
         if(i<high_users)
         {
           num_friends = (num_users * 0.1).toInt
           
           // This function creates a friend list for every user
           // These friends are also chosen randomly from the userDataArray
           createFriends(i,friendList)
           val friendObj = new userData(i,3,friendList, randName , randDob , randPlace)
           userDataArray += friendObj
           
           // Generate random initial posts for each user.
           // These posts are also selected randomly from the data in dataPool
           generatePost(i)
           
           // Generates initial albums and photos for each user
           // This is also created by randomly selecting BASE64 encoding of photos form dataPool 
           generateAlbum(i)
           
           // Generates a list of pages liked by the user
           getLikedPages(i)
         }
         else if(i>=high_users && i<(high_users+mid_users))
         {
           num_friends = (num_users * 0.05).toInt
           
           // This function creates a friend list for every user
           // These friends are also chosen randomly from the userDataArray
           createFriends(i,friendList)
           val friendObj = new userData(i,2,friendList, randName, randDob, randPlace) 
           userDataArray += friendObj
           
           // Generate random initial posts for each user.
           // These posts are also selected randomly from the data in dataPool
           generatePost(i)
           
           // Generates initial albums and photos for each user
           // This is also created by randomly selecting BASE64 encoding of photos form dataPool
           generateAlbum(i)
           
           // Generates a list of pages liked by the user
           getLikedPages(i)
         }
         else
         {
           num_friends = (num_users * 0.02).toInt
           
           // This function creates a friend list for every user
           // These friends are also chosen randomly from the userDataArray
           createFriends(i,friendList)
           val friendObj = new userData(i,1,friendList, randName, randDob, randPlace)
           userDataArray += friendObj
           
           // Generate random initial posts for each user.
           // These posts are also selected randomly from the data in dataPool
           generatePost(i)
           
           // Generates initial albums and photos for each user
           // This is also created by randomly selecting BASE64 encoding of photos form dataPool
           generateAlbum(i)
           
           // Generates a list of pages liked by the user
           getLikedPages(i)
         }
      }
      
      // This function modifies the friend list of user to maintain the reflexive relation
      modifyFriendList
      
      // Prints the information of each user 
      // level denotes the activity of a user : 
      // with 1 being the most active group users and 3 being the least active 
      for(i<-0 to num_users-1)
      {
        println("userid: "+userDataArray(i).uid+", level: "+userDataArray(i).level+", name: "+userDataArray(i).name+", Dob:"+userDataArray(i).dob+", place: "+userDataArray(i).place)
        print("Friends :")
        for(j<-0 to userDataArray(i).friends.length-1)
        {
          print(userDataArray(i).friends(j)+", ")
        }
        println(" ")
      }      
      
      // prints posts for few users
      for(i<-0 to 9)
      {
        print("i: "+i)
        //var temp:ArrayBuffer[String] = postTable(i)
        for(j<-0 to postTable(i).length-1)
        { 
          print(" ##post: "+postTable(i)(j)+", ")
        }
        println(" ")
      }
      
      // Prints pages liked by few users
      for(i<-0 to 9)
      {
        print("i: "+i)
        //var temp:ArrayBuffer[String] = postTable(i)
        for(j<-0 to pageLiked(i).length-1)
        { 
          print(" !!pages Liked: "+pageLiked(i)(j)+", ")
        }
        println(" ")
      }
      
    }
    
    
    // This REST API is called to post a message
    case postMsg(user_id:Int, post:String) => {
      
      // The post sent from the URL is parsed back to regain the whitespaces in the text
      var origStr=post.replaceAll("@", " ")
      println("The new post is " + origStr)
      println("The no of post for userid "+user_id+" is " +postTable(user_id).length)
      postTable(user_id) += origStr
      println("The  news no of post after addition for userid "+user_id+" is " + postTable(user_id).length)
      
      // All posts of the users are printed
      for(j<-0 to postTable(user_id).length-1)
      { 
        print(" @@post: "+postTable(user_id)(j)+", ")
      }
      println(" ")
    }    
    
    // This REST API is called to retrieve the Friends list for the user
    case returnFriends(userId:Int) => {
      println("In the case return friends")
      sender ! returnFriends(userId)      
    }
    
    // This REST API is called to post the image
    case postImg(user_id:Int, albumid:Int, post:String) => {
      var origImg=post.replaceAll("%20", " ")
      println("The received image is " + post)
      imageTable(user_id)(albumid) += origImg
      //println("Please kamkar re bawa")
        /*for(a<-0 to imageTable.length-1)
          for(b<-0 to imageTable(a).length-1)
            for(c<-0 to imageTable(a)(b).length-1)
            {
              println("Image : "+ imageTable(a)(b)(c))
            }*/      
      
      // All images are printed for the user
      for(j<-0 to imageTable(user_id)(albumid).length-1)
      { 
        print(" @@images: " + imageTable(user_id)(albumid)(j)+", ")
      }
      println(" ")
    }
    
    // This REST API is called to retrieve the profile of the user 
    case fetchProfile(user_id:Int) => {
      println("In case fetch profile")
      val myPosts =new ArrayBuffer[String]() with SynchronizedBuffer[String]
      val myFriends = new ArrayBuffer[String]() with SynchronizedBuffer[String]
      val myPictures = new ArrayBuffer[String]() with SynchronizedBuffer[String]
      val myPages = new ArrayBuffer[String]() with SynchronizedBuffer[String]
      for(j<-0 to postTable(user_id).length-1)
      { 
        myPosts += postTable(user_id)(j)
        myPosts += "\n"
        //print(" @@post: "+postTable(user_id)(j)+", ")
      }
      
      for(i<-0 to userDataArray(user_id).friends.length-1)
      {
        myFriends += userDataArray(userDataArray(user_id).friends(i)).name
      }
      
      for(x<-0 to imageTable(user_id).length-1 )
      {
        for(y<-0 to imageTable(user_id)(x).length-1)
        {
          myPictures += imageTable(user_id)(x)(y)
        }
      }
      
      for(i<-0 to pageLiked(user_id).length-1)
      {
        myPages += pages(pageLiked(user_id)(i)).title
      }
      // writePretty converts the data into JSON object and sends it back to the derivative of the startServer
      sender ! writePretty("Name: "+userDataArray(user_id).name+", Date of Birth: "+userDataArray(user_id).dob+", Place of Birth: "+userDataArray(user_id).place+", Friends: "+myFriends+", Pages Liked: "+myPages++ " , Picture : " +myPictures+", Wall: "+myPosts )
    }
    
    // This REST API is called to display a page
    case fetchPage(user_id:Int, page_title:String) => {
      println("In case fetch Page")
      var page_name = page_title.replaceAll("%20", " ")
      val likedBy = new ArrayBuffer[String]() with SynchronizedBuffer[String]
      var page_id = -1
      var ii = 0
      while(ii<5 && page_id == -1)
      {
        if(dataPool.page_titles(ii) == page_name)
          page_id = ii
        ii += 1
      }
      for(i<-0 to num_page_likes(page_id).length-1)
      {
        likedBy += userDataArray(num_page_likes(page_id)(i)).name
      }
      sender ! writePretty("Page Title: "+page_name+", Page Information: "+dataPool.page_info(page_id)+", Page is Liked By: "+likedBy)
    }
    
    // This REST API is called to display photos in a Album for a user
    case fetchAlbum (user_id:Int, album_id:Int) => {
      val usrname:String=userDataArray(user_id).name
      val tempBuffer =new ArrayBuffer[String]()
      for (x<-0 to imageTable(user_id)(album_id).length-1)
      {
        tempBuffer += imageTable(user_id)(album_id)(x)
      }
      sender ! writePretty("Album : " + album_id +" retrieved for user: " +usrname+ " consist of images " +tempBuffer)
    }
    
    // This REST API is called to add a friend for a user 
    case addFriend(user_Id:Int,friend_ID :Int)=> {
      if(!userDataArray(user_Id).friends.contains(friend_ID))
      {
        userDataArray(user_Id).friends +=friend_ID
        
      }
      val myFriends =new ArrayBuffer[String]() with SynchronizedBuffer[String]
      for(i<-0 to userDataArray(user_Id).friends.length-1)
      {
        myFriends += userDataArray(userDataArray(user_Id).friends(i)).name
      }
      sender ! writePretty(myFriends)
    }
    
  }
  
  // This method is used to create friends for a user which returns the friend list to the returnFriends API
  def createFriends(uid:Int, friendList:ArrayBuffer[Int]) = {
    var randFriend:Int = 0
    var i:Int = 0
    while(i<num_friends)
    {      
      randFriend = Random.nextInt(num_users-1)
      if(randFriend != uid && !friendList.contains(randFriend))
      {
        friendList += randFriend
        i += 1
      }
    }
  }
  
  // This method is used to ensure the reflexive property of "FRIENDS" relation
  def modifyFriendList = {
    for(i<-0 to num_users-1)
    {
      for(j<-0 to userDataArray(i).friends.length-1)
      {
        if(!userDataArray(userDataArray(i).friends(j)).friends.contains(i))
        {
          userDataArray(userDataArray(i).friends(j)).friends += i
        }
      }
    }
  }
  
  def returnFriends(userID:Int):String={
    println("In the method return friends")
    val usrname:String=userDataArray(userID).name
    val myFriends =new ArrayBuffer[String]() with SynchronizedBuffer[String]
    //myArray+=userDataArray(userID).friends
    for(i<-0 to userDataArray(userID).friends.length-1)
    {
      myFriends += userDataArray(userDataArray(userID).friends(i)).name
    }
    //return writePretty(userDataArray(userID).friends) 
    return writePretty(usrname+" : "+myFriends)
  }
  
  // This method is used to generate a post for the user
  // The post is selected from the dataPool
  def generatePost(userid:Int) = {
    val tempBuffer=new ArrayBuffer[String]()
    tempBuffer.+=(dataPool.posts(Random.nextInt(dataPool.posts.length)))
    tempBuffer.+=(dataPool.posts(Random.nextInt(dataPool.posts.length)))
    postTable.+=(tempBuffer)
  }
  
  // This method is used to create albums for users 
  def generateAlbum(userid:Int) ={
    val tempAlbum= new ArrayBuffer[ArrayBuffer[String]]()
    for(z<-0 to 4)
    {
    val tempString=new ArrayBuffer[String]()
    tempString.+=(dataPool.imageBase64(Random.nextInt(dataPool.imageBase64.length)))
    tempString.+=(dataPool.imageBase64(Random.nextInt(dataPool.imageBase64.length)))
    tempString.+=(dataPool.imageBase64(Random.nextInt(dataPool.imageBase64.length)))
    tempAlbum.+=(tempString)
    }
    imageTable.+=(tempAlbum)
  }
  
  // This method is used to get the pages that are like by a user
  def getLikedPages(user_id:Int) = {
    var randLikes = 0
    while(randLikes == 0)
     randLikes = Random.nextInt(5)
    //println("raand likes: "+randLikes+" for user "+user_id)
    var mypages = new ArrayBuffer[Int]()
    for(i<-0 to randLikes-1)
    {
       var temp = Random.nextInt(5)
       if(!mypages.contains(temp))
       {
         num_page_likes(temp) += user_id
         mypages += temp
         //println("liked: "+temp+" for user "+user_id)
       }
    }
    pageLiked.+=(mypages)
  }
}
