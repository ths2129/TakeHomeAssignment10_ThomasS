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
        mMessagesDataBaseReference = mFireBaseDatabase.getReference().child("messages");
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

            ;

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


    private void onSignedInIntitialized(String username) {
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

    ;


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
