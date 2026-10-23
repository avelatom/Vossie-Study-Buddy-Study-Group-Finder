package com.v2v.app;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.v2v.app.util.ApiClient;
import com.v2v.app.util.SessionManager;

// Only activity in the app - every screen is a Fragment
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        bottomNav = findViewById(R.id.bottom_nav);

        final View toolbarContainer = findViewById(R.id.toolbar_container);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (rootView, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            toolbarContainer.setPadding(0, bars.top, 0, 0);
            rootView.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = host.getNavController();

        // Start on the home tab if a valid session exists, otherwise on login
        NavInflater inflater = navController.getNavInflater();
        NavGraph graph = inflater.inflate(R.navigation.nav_graph);
        boolean loggedIn = SessionManager.get(this).isLoggedIn();
        graph.setStartDestination(loggedIn ? R.id.homeFragment : R.id.loginFragment);
        navController.setGraph(graph);

        // Bottom tabs + login are top level (no back arrow)
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.homeFragment, R.id.searchFragment, R.id.groupsFragment,
                R.id.connectionsFragment, R.id.messagesFragment, R.id.loginFragment).build();
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // "My profile" lives behind the person icon in the top bar
        toolbar.inflateMenu(R.menu.toolbar_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_profile) {
                NavOptions single = new NavOptions.Builder().setLaunchSingleTop(true).build();
                navController.navigate(R.id.profileFragment, null, single);
                return true;
            }
            return false;
        });

        // Bottom bar and profile icon are hidden on the login / register / verify / chat screens
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            boolean showBar = id == R.id.homeFragment || id == R.id.searchFragment
                    || id == R.id.groupsFragment || id == R.id.connectionsFragment
                    || id == R.id.messagesFragment || id == R.id.profileFragment
                    || id == R.id.skillsFragment || id == R.id.peerProfileFragment
                    || id == R.id.groupDetailFragment;
            bottomNav.setVisibility(showBar ? View.VISIBLE : View.GONE);

            MenuItem profileItem = toolbar.getMenu().findItem(R.id.action_profile);
            if (profileItem != null) {
                boolean loggedInNow = SessionManager.get(MainActivity.this).isLoggedIn();
                profileItem.setVisible(loggedInNow && id != R.id.profileFragment
                        && id != R.id.loginFragment && id != R.id.registerFragment && id != R.id.verifyFragment);
            }
        });

        // Any 401 from the server sends the user back to login
        ApiClient.setUnauthorizedListener(() -> {
            Toast.makeText(MainActivity.this, "You have been logged out. Please log in again.", Toast.LENGTH_LONG)
                    .show();
            goToLogin();
        });
    }

    // Clears the whole back stack and shows the login screen.
    public void goToLogin() {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.loginFragment) {
            return;
        }
        navController.getGraph().setStartDestination(R.id.loginFragment);
        NavOptions options = new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build();
        navController.navigate(R.id.loginFragment, null, options);
    }

    // Clears the back stack and shows the home (suggested matches) tab.
    public void goToHome() {
        navController.getGraph().setStartDestination(R.id.homeFragment);
        NavOptions options = new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build();
        navController.navigate(R.id.homeFragment, null, options);
    }

    // Lets a screen change the title (e.g. student's name in a chat)
    public void setToolbarTitle(String title) {
        toolbar.setTitle(title);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }
}
