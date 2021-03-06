 package main

import akka.actor._
import spray.http._
import spray.client.pipelining._
//import globalData.dataPool
import scala.util.Random


/**
 * @author Drumil Deshpande
   @author Sumeet Pande
 */

class user(num_users:Int,userId : Int, actSys:ActorSystem) extends Actor{
  
  import actSys.dispatcher
  val pipeline2 = sendReceive
  val pipeline3 = sendReceive
  val client = context.actorSelection("akka://FacebookSimulator/user/clientSystem")
  
  def receive = {
      
    // This case is called when a user wants to post a new facebook post.
    case "postNewMsg" => {
      println("In user : "+userId)
      var origStr:String = dataPool.posts(Random.nextInt(dataPool.posts.length))
      
      // The white spaces in string need to be parse in order to send then through URL
      // The standard way to do this is to replace the whitespaces wiht "%20" in the URL
      var postStr:String= origStr.replaceAll(" ", "%20")
      pipeline2(Post("http://localhost:8080/facebook/facebookPost?userid="+userId+"&fbPost="+postStr))
      //client ! "done"
    }
    
    
    // This method is called when the user wishes to view his friends
    case "returnFriends" =>{
      val result = pipeline3(Get("http://localhost:8080/facebook/returnFriends?userid="+userId))
      //println(result.toString)
      println("Getting Friend List for user: " +userId )
      // Prints the Json obejct recieved from the server.
      result.foreach 
      { 
        response => println(s"facebook request completed ${response.status} and content:\n${response.entity.asString}")
      }
      //client ! "postImage"
    }
    
    
    // This method is called when the user wishes to post an Image.
    // A standard way to send images and store then is to encode them in BASE64 which is used here
    case "postImage" => {
      println("In user for posting image: "+userId)
      
      // Encodes the string to BASE64
      var origImg:String =dataPool.imageBase64(Random.nextInt(dataPool.imageBase64.length))
      println("The sent image length is " + origImg.length())
      var postImg:String= origImg.replaceAll(" ", "%20")
    
      //An Album ID is sleceted for the image.
      var albID=Random.nextInt(4)
      pipeline2(Post("http://localhost:8080/facebook/imagePost?userid="+userId+"&albumID="+albID+"&imagePost="+postImg))
    }
    
    // This method is called when the users wishes to see his profile.
    case "getProfile" => {
      val result = pipeline2(Get("http://localhost:8080/facebook/getProfile?userid="+userId))
      println("Getting Profile for user: " +userId )
      
      // Prints the JSON object received for the GET request
      result.foreach 
      { 
        response => println(s"facebook request completed ${response.status} and content:\n${response.entity.asString}")
      }
    }
    
    // This method is called when user whises to seea facebook page.
    case "viewPage" => {
      
      // A page is selected to be viewed depending upon the page name.
      var page_title = dataPool.page_titles(Random.nextInt(5))
      var page_title_op = page_title.replaceAll(" ", "%20")
      val result = pipeline2(Get("http://localhost:8080/facebook/page?userid="+userId+"&pageTitle="+page_title_op))
      
      // Prints the JSON object received for the GET request for page.
      result.foreach 
      { 
        response => println(s"facebook request completed ${response.status} and content:\n${response.entity.asString}")
      }  
    }
    
    // This method is called when the users wishes to view an Album of photos.
    case "viewAlbum" => {
      
      // An album is selected to be viewed
      var albID=Random.nextInt(4) 
      val result = pipeline2(Get("http://localhost:8080/facebook/getAlbum?userid="+userId+"&albumID="+albID))
      
      // This prints the JSON object that contains photos (encoded as BASE64 strings) for the requested Album
      result.foreach 
      { 
        response => println(s"facebook request completed ${response.status} and content:\n${response.entity.asString}")
      }  
    }
    
    
    // This method is called when the user wishes to add a firend
    case "addFriend" => {
      var newFriend:Int = userId
      
      // If the person is not already a friend of the user, only then that user can be requested to be added as a friend
      while (newFriend == userId)
        newFriend = Random.nextInt(num_users)
      val result = pipeline2(Get("http://localhost:8080/facebook/addNewFriend?userid="+userId+"&friendID="+newFriend))
      
      // This prints the JSON object containing update friend list for the user
      result.foreach 
      { 
        response => println(s"facebook request completed ${response.status} and content:\n${response.entity.asString}")
      }
    }
  }
}
