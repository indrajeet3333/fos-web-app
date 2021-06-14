package controllers;

import play.mvc.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import org.mindrot.jbcrypt.BCrypt;
import scala.Int;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {
    //Local DB Settings
    // static String dbUrl="jdbc:mysql://localhost:3306/fos",dbUser="root",dbPass="root"
    //Heroku Prod DB Settings
    static String dbUrl="jdbc:postgresql://ec2-34-193-113-223.compute-1.amazonaws.com:5432/dea9t3pb4r6gal?sslmode=require",dbUser="uyiykvbdzgiqvz",dbPass="18689f07325e62dc7990152e01b96c053ed41c266889bf03ac811d4014342e8a";
    static Connection conn;
    static PreparedStatement pst;
    static ResultSet resultSet;
    static ResultSetMetaData resultSetmd;
    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index(Http.Request request) {
        String loginStatus = session("loginStatus");
        System.out.println("Login Status Object: "+loginStatus);
        return ok(views.html.index.render(loginStatus)).removingFromSession(request,"loginStatus");
    }

    public Result login(Http.Request request) {
        String user_name = null,pHash = null,uid=null,enteredPass = null,enteredEmail=null;
        int uzip = 0;
        var formData = request.body().asFormUrlEncoded();  //Retrieve FORM data from POST request
        enteredEmail = formData.get("uemail")[0];          //Retrieve email entered by user
        enteredPass = formData.get("upwd")[0];             //Retrieve password entered by user
        connectToMySQL();                                  //Connect to mySQL
        if (conn == null) return badRequest("Connection to MySQL database failed. Please make sure that you have your SQL server up and running on port localhost:3306");
        try {
            pst = conn.prepareStatement("select * from users where uemail = ?");  //Login Query
            pst.setString(1, enteredEmail);
            resultSet = pst.executeQuery();

            if (!resultSet.next()){     //If DB contains no user with given email, then redirect to index page
                session("loginStatus","inv-email");
                return redirect(routes.HomeController.index()); //If not authenticated, redirect to index page
            }else {                     //Get data from the DB for the queried user
                uid = resultSet.getString("uid");          //Get userid
                uzip = resultSet.getInt("uzip");        //Get user's zipcode
                user_name = resultSet.getString("uname");  //Get username
                pHash = resultSet.getString("upwd");       //Get user's password hash
            }

            //Authenticate User with the corresponding password
            if (BCrypt.checkpw(enteredPass, pHash)){
                session("user",user_name);
                session("uid",uid);
                session("uzip", String.valueOf(uzip));
                return redirect(routes.HomeController.home());  //If authenticated, redirect to home page
            }
            else{
                session("loginStatus","inv-pass");
                return redirect(routes.HomeController.index()); //If not authenticated, redirect to index page
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        session("loginStatus","unknown");
        return redirect(routes.HomeController.index());
    }
    public Result signup(Http.Request request) {
        String name = null,email = null,pass = null;
        Integer zip = null;
        var formData = request.body().asFormUrlEncoded();
        zip   = Integer.parseInt(formData.get("uzip")[0]);
        name  = formData.get("uname")[0];
        email = formData.get("uemail")[0];
        pass  = formData.get("upwd")[0];
        String hashedpwd = BCrypt.hashpw(pass, BCrypt.gensalt()); //Password Hashing
        connectToMySQL();
        try {
            pst = conn.prepareStatement("INSERT INTO users(uname,uzip,uemail,upwd) VALUES (?,?,?,?)"); //Sign up Query
            pst.setString(1, name);
            pst.setInt(2, zip);
            pst.setString(3, email);
            pst.setString(4, hashedpwd);
            pst.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return ok(name+"\n"+zip+"\n"+email+"\n"+pass+"\n"+"\nSign Up Successful");
    }

    public Result home(Http.Request request) {
        //Objects already in session till now -  user:<name>; uid:<id>; uzip: zipcode of user in string(convert to int for use)
        String userName = session("user"),userId = session("uid");
        int uzip = Integer.parseInt(session("uzip"));
        List<Integer> bids = new ArrayList<>();
        List<String> branches = new ArrayList<>();
        List<String> vendors = new ArrayList<>();
        connectToMySQL();
        try {
            pst = conn.prepareStatement("SELECT v.vname,b.address,b.branchid FROM vendor v, branch b WHERE v.vendorid=b.vendorid AND b.zcode = ?");
            pst.setInt(1, uzip);
            pst.executeQuery();
            resultSet = pst.executeQuery();

            if (!resultSet.next()){        //If DB contains no vendors in the given zip, then say sorry by passing no to resAvail variable.
                return ok(views.html.home.render(userId,userName,vendors,branches,bids,"no"));
            }else {                       //Get data from the DB for branches in that zip
                resultSet.previous();
                while (resultSet.next()){
                    vendors.add(resultSet.getString("vname"));      //Get Vendor Name
                    branches.add(resultSet.getString("address"));   //Get Vendor Address
                    bids.add(resultSet.getInt("branchid"));         //Get Branch Id
                }
            }
            conn.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return ok(views.html.home.render(userId,userName,vendors,branches,bids,"yes"));  //send data to view to populate restaurants
        //Objects already in session till now -  user:<name>; uid:<id>; uzip: zipcode of user in string(convert to int for use)
    }

    public Result updateUser(Http.Request request) {
        var formData = request.body().asFormUrlEncoded();
        var userId = formData.get("userid")[0];
        var userName = "";
        var userZip = "";
        var userEmail = "";

        connectToMySQL();
        try {
            pst = conn.prepareStatement("select * from users where uid = ?");  //Login Query
            pst.setString(1, userId);
            resultSet = pst.executeQuery();
            resultSet.next();
            userName = resultSet.getString("uname");       //Get username
            userZip = resultSet.getString("uzip");         //Get user's zipcode
            userEmail = resultSet.getString("uemail");     //Get user's email
            conn.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return ok(views.html.update.render(userName,userZip,userEmail,"no"));
    }

    public Result updateUserDetails(Http.Request request) {
        var formData = request.body().asFormUrlEncoded();
        var userId = session("uid");
        var userName = formData.get("uname")[0];
        var userZip = formData.get("uzip")[0];
        var userEmail = formData.get("uemail")[0];

        connectToMySQL();
        try {
            pst = conn.prepareStatement("UPDATE Users SET uname = ?, uzip = ?, uemail = ? where uid = ?");  //Update Query
            pst.setString(1, userName);
            pst.setString(2, userZip);
            pst.setString(3, userEmail);
            pst.setString(4, userId);
            pst.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return ok(views.html.update.render(userName,userZip,userEmail,"yes"));
    }

    public Result menu(Http.Request request,Integer bid) {
        String vendorName = null,vendorAddress = null;
        int uid = Integer.parseInt(session("uid"));
        Double tax = 0.0;
        List<Integer> itemIds = new ArrayList<>();
        List<String>  Items = new ArrayList<>();
        List<Double>  Prices = new ArrayList<>();
        connectToMySQL();
        try {
            pst = conn.prepareStatement("SELECT v.vname,b.address,i.itemId,i.itemname,i.price FROM vendor v NATURAL JOIN branch b, item i " +
                    "where i.vendorid = v.vendorid and b.branchid = ?");
            pst.setInt(1, bid);
            pst.executeQuery();
            resultSet = pst.executeQuery();

            if (!resultSet.next()){        //If DB contains no items for the given vendor, then say sorry by passing no to itemsAvail variable.
                return ok(views.html.menu.render(uid,bid,tax,vendorName,vendorAddress,Items,Prices,itemIds,"no"));
            }else {                       //Get data from the DB for items in that branch/vendor
                resultSet.previous();
                while (resultSet.next()){
                    itemIds.add(resultSet.getInt("itemId"));         //Get Item ID
                    Items.add(resultSet.getString("itemname"));      //Get Item name
                    Prices.add(resultSet.getDouble("price"));        //Get Item Price
                    vendorName = resultSet.getString("vname");       //Get Restaurant Name
                    vendorAddress = resultSet.getString("address");  //Get Restaurant Address
                }
            }

            pst = conn.prepareStatement("SELECT t.Percent FROM Branch b NATURAL JOIN Location l NATURAL JOIN Tax t WHERE b.BranchId = ?");
            pst.setInt(1, bid);
            pst.executeQuery();
            resultSet = pst.executeQuery();
            while (resultSet.next()){
                tax = resultSet.getDouble("Percent");         //Get the Tax for that branch id using location and tax table
            }
            conn.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        session("vendor",vendorName);
        session("vadd",vendorAddress);
        return ok(views.html.menu.render(uid,bid,tax,vendorName,vendorAddress,Items,Prices,itemIds,"yes"));
    }
    public Result order(Http.Request request) {
        var formData = request.body().asFormUrlEncoded();
        var itemNames = formData.get("itemName");
        var itemQtys = formData.get("iqty");
        var itemPrcs = formData.get("iprc");
        var userId = formData.get("uid")[0];
        var branchId = formData.get("bid")[0];
        var orDate = formData.get("dt")[0];
        var orSubT = formData.get("subtotal")[0];
        var orTax = formData.get("tax")[0];
        var orPrice = formData.get("price")[0];
        List<Integer> itemIds = new ArrayList<>();
        Integer orderId = 0;
        connectToMySQL();
        try {
            //Create a new order first
            pst = conn.prepareStatement("INSERT INTO Orders (uid,BranchId,ordate,price) VALUES (?,?,?,?)");
            pst.setInt(1, Integer.parseInt(userId));
            pst.setInt(2, Integer.parseInt(branchId));
            pst.setString(3, orDate);
            pst.setFloat(4, Float.parseFloat(orPrice.substring(1)));
            pst.executeUpdate();

            //Get the order id of the newly created order
            pst = conn.prepareStatement("SELECT max(orderid) from orders");
            resultSet = pst.executeQuery();
            resultSet.next();
            orderId = resultSet.getInt(1);

            //Build the SQL to get Item Ids from Item Names
            StringBuilder iNames = new StringBuilder("(");
            for (int i = 0; i < itemNames.length; i++) {
                if (i != itemNames.length-1)
                    iNames.append("'" + itemNames[i] + "',");
                else
                    iNames.append("'" + itemNames[i] + "'");
            }
            iNames.append(")");
            //Get the item Item Ids from the names of the items that user ordered.
            pst = conn.prepareStatement("SELECT * FROM item WHERE ItemName IN "+iNames);
            resultSet = pst.executeQuery();
            while (resultSet.next())
                itemIds.add(resultSet.getInt(1));

            //Insert details into Order Detail table
            pst = conn.prepareStatement("INSERT INTO OrderDetail VALUES (?,?,?,?)");
            for (int i = 0; i < itemNames.length; i++) {
                pst.setInt(1, orderId);
                pst.setInt(2, itemIds.get(i));
                pst.setInt(3, Integer.parseInt(itemQtys[i]));
                pst.setFloat(4, Float.parseFloat(itemPrcs[i]));
                pst.executeUpdate();
            }

            conn.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        session("subT",orSubT);
        session("tax",orTax);
        session("orderPrice",orPrice);
        session("orderId", String.valueOf(orderId));
        //return ok("Order Details:\n"+"Order Id: "+orderId);
        return redirect(routes.HomeController.orderDetail());
    }

    public Result orderDetail(Http.Request request) {
        String oid = session("orderId"),customerName = session("user"),vendorName = session("vendor"),
                vendorAddress = session("vadd"),orderPrice = session("orderPrice"),tax = session("tax"),orderSubT=session("subT");
        List<String> itemNames = new ArrayList<>();
        List<Integer> itemQty = new ArrayList<>();
        List<Double> itemPrice = new ArrayList<>();
        connectToMySQL();
        try {
            pst = conn.prepareStatement("SELECT i.ItemName,o.quantity,o.total from OrderDetail o NATURAL JOIN Item i WHERE OrderId = ?");
            pst.setInt(1, Integer.parseInt(oid));
            pst.executeQuery();
            resultSet = pst.executeQuery();
            while (resultSet.next()){
                itemNames.add(resultSet.getString("ItemName"));
                itemQty.add(resultSet.getInt("quantity"));
                itemPrice.add(resultSet.getDouble("total"));
            }
            conn.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return ok(views.html.ordersummary.render(oid,customerName,vendorName,vendorAddress,itemNames,itemQty,itemPrice,orderSubT,tax,orderPrice));
    }

    public static void connectToMySQL(){
        try {
            conn = DriverManager.getConnection(dbUrl,dbUser,dbPass);
            System.out.println("Connection to MySQL Database established.");
        } catch (SQLException e) {
            System.err.print("Error Occured:");
            System.err.println(e.getMessage());
        }
    }
}
