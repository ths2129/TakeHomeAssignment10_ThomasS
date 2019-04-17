package com.example.takehomeassignment10_thomass;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.data.model.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;


import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 130; //limit
    private static final int RC_PHOTO_PICKER = 2;
    private static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length"; //connects to reconfig


    public static final int RC_SIGN_IN = 1; // Request Code


    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener; //executes when state changes - sign-in and sign-out code (Resume + Pause)

    private FirebaseDatabase mFireBaseDatabase; //entry point for app and database
    private DatabaseReference mMessagesDataBaseReference; //class that references the messages portion
    private ChildEventListener mChildEventListener;
    private FirebaseStorage mFireBaseStorage; // needs getinstance
    private StorageReference mChatPhotoStorageReference;
    private FirebaseRemoteConfig mFirebaseRemoteConfig; //isn't accepting this object???
    private FirebaseRemoteConfigSettings mFirebaseSettings;
    private HashMap<String, Object> configDefault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;
        mAuth = FirebaseAuth.getInstance();
        mFireBaseStorage = FirebaseStorage.getInstance();
        mFireBaseDatabase = FirebaseDatabase.getInstance();// access point - instantianted
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        mMessagesDataBaseReference = mFireBaseDatabase.getReference().child("messages"); //initialized to this location
        mChatPhotoStorageReference = mFireBaseStorage.getReference().child("chat_photos");
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);

        fetchConfig();

        final FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
// reference to root and messages portion of database

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });
        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() { //cannot send an empty message!
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                mMessagesDataBaseReference.push().setValue(friendlyMessage);


                // Clear input box
                mMessageEditText.setText("");
            }
        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener() { //state listener is created, but attached yet, that is onResume emthod
            //This is always pop up the logins
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) { //parameter will reveal if authenticated
                FirebaseUser user = firebaseAuth.getCurrentUser(); //is the user logged in? Conditionas will check

                if (user != null) { //user logged in?
                    //user is signedin and Toast helps user know
                    //Toast.makeText(MainActivity.this, "You're now signed in. Welcome to FriendlyChat.", Toast.LENGTH_SHORT).show();
                    onSignedIntitialized(user.getDisplayName());//pass in username into helper emthod
                } else { // signed out?
                    onSignedOutCleanup();
                    //user is signed out
                    // Choose authentication providers
                    List<AuthUI.IdpConfig> providers = Arrays.asList(
                            new AuthUI.IdpConfig.EmailBuilder().build(),
                            new AuthUI.IdpConfig.GoogleBuilder().build());
                    // new AuthUI.IdpConfig.FacebookBuilder().build();

                    // Create and launch sign-in intent
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(true)// save credentials!!
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN); // Defined constant - Flag for return

                }
            }

            ;
        };

//
        /// public FirebaseRemoteConfigSettings getConfigSettings() {
        // return configSettings;
        configDefault = new HashMap<>();
        configDefault.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults(configDefault); //set up remote config
        mFirebaseRemoteConfig.fetch(0);//fetchConfig();

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder() //enable devloper mode with remote config
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);
        //return configSettings;

        //define parameters

    }

    // Fetch the config to determine the allowed length of messages.
    public void fetchConfig() {
        long cacheExpiration; // 1 hour in seconds
        // If developer mode is enabled reduce cacheExpiration to 0 so that
        //each fetch goes to the server. This should not be used in release builds.
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings()
                .isDeveloperModeEnabled()) { //latest values fro, firebase
            cacheExpiration = 0;

            // this helps with debugging?

            mFirebaseRemoteConfig.fetch(cacheExpiration) //return values
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {  //friendly message length below
                            // Make the fetched config available
                            // via FirebaseRemoteConfig get<type> calls, e.g., getLong, getString.
                            mFirebaseRemoteConfig.fetch();


                            // Update the EditText length limit with
                            // the newly retrieved values from Remote Config.
                            applyRetrievedLengthLimit();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            // An error occurred when fetching the config.

                            // Update the EditText length limit with
                            // the newly retrieved values from Remote Config.
                            applyRetrievedLengthLimit(); //Offline
                        }
                    });
        }
    }

    /**
     * Apply retrieved length limit to edit text field. This result may be fresh from the server or it may be from
     * cached values.
     */

    private void applyRetrievedLengthLimit() { //this updates text length
        Long friendly_msg_length = mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(friendly_msg_length.intValue())});
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) { //sings in user
                Toast.makeText(this, "Signed in", Toast.LENGTH_SHORT).show();

            } else if (resultCode == RESULT_CANCELED) { //user cancelled
                finish();

                //file name
            } else if (requestCode == RC_PHOTO_PICKER && requestCode == RESULT_OK) {
                Uri selectedImageUri = data.getData();

                final StorageReference photoRef = mChatPhotoStorageReference.child((selectedImageUri.getLastPathSegment())); //made a child in the storage reference - named after the last part of segment
                photoRef.putFile(selectedImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        //When the image has successfully uploaded, get its download URL
                        photoRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                Uri Uri = uri;
                                FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, Uri.toString());
                                mMessagesDataBaseReference.push().setValue(friendlyMessage);
                            }
                        });
                    }//saved selected image in storage
                });

            }
        }
    }


    private void onSignedIntitialized(String username) {
        mUsername = username; //variable linked to the sendBUtton method onClick
        //sets username
        attachDatabaseReadListener(); //called listeners

        //Only sending user name and messager list when logged in

    }


    private void attachDatabaseReadListener() {
        if (mChildEventListener == null)
            ; //if only it has been detached will the listener work
        mChildEventListener = new ChildEventListener() { //onbject reponds with new message
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                mMessageAdapter.add(friendlyMessage);

            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { //changed when changed

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        };
        mMessagesDataBaseReference.addChildEventListener(mChildEventListener); // this will trigger from the listeners
    }


    private void onSignedOutCleanup() {
        mUsername = ANONYMOUS; //sets back to original
        mMessageAdapter.clear();//user not signed will be detached
        //unset username detack listener
    }

    private void detachDatabaseRedListener() {
        if (mChildEventListener != null) {
            mMessagesDataBaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;

        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }


    @Override
    protected void onPause() { // passed on onStateListener
        super.onPause();
        if (mChildEventListener != null) { //detached listener
            mAuth.removeAuthStateListener(mAuthStateListener); //on off states
        }
        detachDatabaseRedListener();
        mMessageAdapter.clear(); // activity is destoryed and app cleans up
    }

    @Override
    protected void onResume() { //onState listener
        super.onResume();
        mAuth.addAuthStateListener(mAuthStateListener); //passed in listener
    }


}

/*

package com.example.takehomeassignment10_thomass;


        import android.content.Intent;
        import android.os.Bundle;
        import android.support.annotation.NonNull;
        import android.support.annotation.Nullable;
        import android.support.v7.app.AppCompatActivity;
        import android.text.Editable;
        import android.text.InputFilter;
        import android.text.TextWatcher;
        import android.view.Menu;
        import android.view.MenuInflater;
        import android.view.MenuItem;
        import android.view.View;
        import android.widget.Button;
        import android.widget.EditText;
        import android.widget.ImageButton;
        import android.widget.ListView;
        import android.widget.ProgressBar;
        import android.widget.Toast;

        import com.firebase.ui.auth.AuthUI;
        import com.google.firebase.FirebaseApp;
        import com.google.firebase.auth.FirebaseAuth;
        import com.google.firebase.auth.FirebaseUser;
        import com.google.firebase.database.ChildEventListener;
        import com.google.firebase.database.DataSnapshot;
        import com.google.firebase.database.DatabaseError;
        import com.google.firebase.database.DatabaseReference;
        import com.google.firebase.database.FirebaseDatabase;

        import java.util.ArrayList;
        import java.util.Arrays;
        import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 240; //limit

    public static final int RC_SIGN_IN = 1; // Request Code


    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener; //executes when state changes - sign-in and sign-out code (Resume + Pause)

    private FirebaseDatabase mFireBaseDatabase; //entry point for app and database
    private DatabaseReference mMessagesDataBaseReference; //class that references the messages portion
    private ChildEventListener mChildEventListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mUsername = ANONYMOUS;
        mAuth = FirebaseAuth.getInstance();
        mFireBaseDatabase = FirebaseDatabase.getInstance();// access point - instantianted
        //reference to root note
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);

        final FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
// reference to root and messages portion of database

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() { //cannot send an empty message!
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                mMessagesDataBaseReference.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener() { //state listener is created, but attached yet, that is onResume emthod
            //This is always pop up the logins
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) { //parameter will reveal if authenticated
                FirebaseUser user = firebaseAuth.getCurrentUser(); //is the user logged in? Conditionas will check

                if (user != null) { //user logged in?
                    //user is signedin and Toast helps user know
                    //Toast.makeText(MainActivity.this, "You're now signed in. Welcome to FriendlyChat.", Toast.LENGTH_SHORT).show();
                    onSignedInIntitialized(user.getDisplayName());//pass in username into helper emthod
                } else { // signed out?
                    onSignedOutCleanup();
                    //user is signed out
                    // Choose authentication providers
                    List<AuthUI.IdpConfig> providers = Arrays.asList(
                            new AuthUI.IdpConfig.EmailBuilder().build(),
                            new AuthUI.IdpConfig.GoogleBuilder().build());
                    // new AuthUI.IdpConfig.FacebookBuilder().build();

                    // Create and launch sign-in intent
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(true)// save credentials!!
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN); // Defined constant - Flag for return

                }
            }



        };
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (requestCode == RESULT_OK) { //sings in user
            } else if (resultCode == RESULT_CANCELED) { //user cancelled
                finish();
            }

        }
        ;
    }



    private void attachDatabaseReadListener() {
        if (mChildEventListener == null)
            ; //if only it has been detached will the listener work
        mChildEventListener = new ChildEventListener() { //onbject reponds with new message
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                // FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                //  mMessageAdapter.add(friendlyMessage);

            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { //changed when changed

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        };
        mMessagesDataBaseReference.addChildEventListener(mChildEventListener); // this will trigger from the listeners

    }






    private void onSignedInIntitialized(String username) {
        mUsername = username; //variable linked to the sendBUtton method onClick
        //sets username
        attachDatabaseReadListener(); //called listeners

        //Only sending user name and messager list when logged in

    }

    private void onSignedOutCleanup() {
        mUsername = ANONYMOUS; //sets back to original
        mMessageAdapter.clear();//user not signed will be detached
        //unset username detack listener
    }


    private void detachDatabaseRedListener() {
        if (mChildEventListener != null) {
            mMessagesDataBaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }


    @Override
    protected void onPause() { // passed on onStateListener
        super.onPause();
        if (mChildEventListener != null) { //detached listener
            mAuth.removeAuthStateListener(mAuthStateListener); //on off states
        }
        detachDatabaseRedListener();
        mMessageAdapter.clear(); // activity is destoryed and app cleans up
    }

    @Override
    protected void onResume() { //onState listener
        super.onResume();
        mAuth.addAuthStateListener(mAuthStateListener); //passed in listener
    }


}
*/
