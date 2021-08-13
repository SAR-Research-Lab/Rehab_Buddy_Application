package com.probmatic.grapher;

        import android.app.Activity;
        import android.app.ProgressDialog;
        import android.content.Intent;
        import android.net.Uri;
        import android.os.Bundle;
        import android.os.Handler;
        import android.util.Log;
        import android.view.View;
        import android.widget.Button;
        import android.widget.ImageView;
        import android.widget.TextView;
        import android.widget.Toast;

        import androidx.annotation.NonNull;
        import androidx.appcompat.app.AppCompatActivity;
        import androidx.core.content.FileProvider;

        import com.google.android.gms.auth.api.signin.GoogleSignIn;
        import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
        import com.google.android.gms.auth.api.signin.GoogleSignInClient;
        import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
        import com.google.android.gms.common.SignInButton;
        import com.google.android.gms.common.api.ApiException;
        import com.google.android.gms.common.internal.Constants;
        import com.google.android.gms.tasks.OnCompleteListener;
        import com.google.android.gms.tasks.OnFailureListener;
        import com.google.android.gms.tasks.OnSuccessListener;
        import com.google.android.gms.tasks.Task;
        import com.google.firebase.auth.AuthCredential;
        import com.google.firebase.auth.AuthResult;
        import com.google.firebase.auth.FirebaseAuth;
        import com.google.firebase.auth.FirebaseUser;
        import com.google.firebase.auth.GoogleAuthProvider;
        import com.google.firebase.database.DatabaseReference;
        import com.google.firebase.database.FirebaseDatabase;
        import com.google.firebase.storage.FirebaseStorage;
        import com.google.firebase.storage.OnProgressListener;
        import com.google.firebase.storage.StorageReference;
        import com.google.firebase.storage.UploadTask;

        import android.content.Intent;
        import android.graphics.Bitmap;
        import android.net.Uri;
        import android.provider.MediaStore;
        import android.os.Bundle;
        import android.view.View;
        import android.widget.Button;
        import android.widget.ImageView;

        import java.io.File;

public class UserActivity<ActivityGoogleBinding> extends AppCompatActivity {

    private static final String TAG = "UserActivity";
    private static final int RC_SIGN_IN = 9001;

    // [START declare_auth]
    private FirebaseAuth mAuth;
    // [END declare_auth]

    private GoogleSignInClient mGoogleSignInClient;
    private ActivityGoogleBinding mBinding;

    TextView statusTextView;
    private DatabaseReference mDatabase;

    private static final int PICK_IMAGE_REQUEST = 234;

    //Buttons
    private Button buttonChoose;
    private Button buttonUpload;

    //ImageView
    private ImageView imageView;

    //a Uri object to store file path
    private Uri filePath;
    DataWriter csvData;
    File data;


    private StorageReference mStorageRef;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);


        SignInButton bsignIn; //sign in button
        Button bsignOut; //sign out button
        Button bDataBase; //database temp button
        
        // [START config_signin]
        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        // [END config_signin]

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // [START initialize_auth]
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        // [END initialize_auth]

        mStorageRef = FirebaseStorage.getInstance().getReference();

//TODO: Create logic that hides the sign in/sign out buttons when a user is already signed in or signed out.
        bsignIn = (SignInButton) findViewById(R.id.bsignIn); //logic for signing in tied to the sign out button. (Always is displayed, regardless if user is already logged in)
        bsignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signIn();
            }
        });

        bsignOut = (Button) findViewById(R.id.bsignOut); //logic for signing out tied to the sign out button. (Always is displayed, regardless if user is already logged out)
        bsignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signOut();
            }
        });

        //added
        bDataBase = (Button) findViewById(R.id.bDataBaseSample); //logic for signing out tied to the register user in DB button. (Always is displayed, regardless if user isn't already logged in)
        bDataBase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //createNewUser();
            }
        });

        buttonChoose = (Button) findViewById(R.id.buttonChoose);
        buttonChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //showFileChooser();

            }
        });
        buttonUpload = (Button) findViewById(R.id.buttonUpload);
        buttonChoose.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                uploadFile();

            }
        });

    }

    private void uploadFile() {
        Intent data = new Intent(Intent.ACTION_GET_CONTENT);
        data.addCategory(Intent.CATEGORY_OPENABLE);
        data.setType("text/csv");
        //startActivityForResult(Intent.createChooser(data, "Open CSV"), ACTIVITY_CHOOSE_FILE1;


        //Uri file = Uri.fromFile(new File("com.probmatic.grapher.fileprovider"));
        //File file2 = data;
        //Uri filz = FileProvider.getUriForFile(file2), mimeType);
        //StorageReference riversRef = mStorageRef.child("images/"+file2.getLastPathSegment());
        //Intent fileIntent = new Intent(Intent.ACTION_SEND);
        //fileIntent.setType("text/csv");
        //fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        //fileIntent.putExtra(Intent.EXTRA_STREAM, file);
        //startActivity(Intent.createChooser(fileIntent, "Open Recorded Data"));
    }


    // [START on_start_check_user]
    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            signOut(); //Gets rid of any weird logic that may arise if a user opens the app and is already logged in
        }

    }

    // [END on_start_check_user]
    // [START onactivityresult]
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Google Sign In was successful, authenticate with Firebase
                final GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                Log.d(TAG, "Firebase Auth UID Token Preload:" + mAuth.getUid());
                firebaseAuthWithGoogle(account.getIdToken());
                statusTextView = (TextView) findViewById(R.id.status_textview);

                statusTextView.setText(account.getDisplayName() + ", google sign in was successful! \n\nNot " + account.getDisplayName() + "?\nClick the SIGN OUT button and then the SIGN IN button to log in with your account.");

                //mDatabase.child("users").setValue(mAuth.getUid());
                new Handler().postDelayed(new Runnable() { //need to delay to allow for ID to initialize

                    @Override
                    public void run() {
                        Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                        Log.d(TAG, "Firebase Auth UID Token:" + mAuth.getUid());
                        String ID = mAuth.getUid();
                        String gender = "female";


                        //TODO: THIS IS A HARDCODED EXAMPLE. EXPAND ON THIS CONCEPT TO IMPLEMENT CSV DATA
                        String username = account.getDisplayName();
                        DatabaseReference database = FirebaseDatabase.getInstance().getReference();

                        database.child("users").child(ID).child("username").setValue(username); //add user data
                        database.child("users").child(ID).child("Gender").setValue(gender); //hard coded create user fields to enter in if needed


                        //database.push().setValue(mAuth.getUid());
                        //.child("name").setValue(username);
                        //database.push().setValue(username);
                    }
                }, 3000); // Millisecond 1000 = 1 sec

            } catch (ApiException e) {
                // Google Sign In failed, update UI appropriately
                statusTextView = (TextView) findViewById(R.id.status_textview);
                statusTextView.setText("Google sign in failed. Try logging in again");
                Log.w(TAG, "Google sign in failed", e);
                // [START_EXCLUDE]

                // [END_EXCLUDE]
            }

        }
    }
    // [END onactivityresult]
    // [START auth_with_google]
    private void firebaseAuthWithGoogle(String idToken) {
        // [START_EXCLUDE silent]
        // [END_EXCLUDE]
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");
                            statusTextView = (TextView) findViewById(R.id.status_textview);
                            //statusTextView.setText("Signed In");

                        } else {
                            // If sign in fails, display a message to the user.
                            statusTextView = (TextView) findViewById(R.id.status_textview);
                            statusTextView.setText("Sign in failed.");
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                        }
                        // [START_EXCLUDE]
                        // [END_EXCLUDE]
                    }
                });
    }
    // [END auth_with_google]
    // [START signin]
    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }
    // [END signin]

    //TODO: As it is right now, if the user were to log in/log out extremely fast, the app will crash. Need to contain logic to have the signout button on a delay.
    private void signOut() {
        // Firebase sign out
        mAuth.signOut();
        statusTextView = (TextView) findViewById(R.id.status_textview);
        statusTextView.setText("No user is currently logged in. Please click SIGN IN button to log into your account.");
        // Google sign out
        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                    }
                });
    }
}