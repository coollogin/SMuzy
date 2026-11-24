package io.github.coollogin.smuzy.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;

import io.github.coollogin.smuzy.App;
import io.github.coollogin.smuzy.R;
import io.github.coollogin.smuzy.ui.login.LoginActivity;
import io.github.coollogin.smuzy.ui.adapter.ServerAdapter;
import io.github.coollogin.smuzy.databinding.FragmentLoginBinding;
import io.github.coollogin.smuzy.interfaces.ClickCallback;
import io.github.coollogin.smuzy.interfaces.SystemCallback;
import io.github.coollogin.smuzy.model.Server;
import io.github.coollogin.smuzy.repository.PlaylistRepository;
import io.github.coollogin.smuzy.repository.SystemRepository;
import io.github.coollogin.smuzy.ui.activity.MainActivity;
import io.github.coollogin.smuzy.util.Preferences;
import io.github.coollogin.smuzy.viewmodel.LoginViewModel;

import java.util.Objects;

@UnstableApi
public class LoginFragment extends Fragment implements ClickCallback {
    private static final String TAG = "LoginFragment";

    private FragmentLoginBinding bind;
    private MainActivity activity;
    private LoginViewModel loginViewModel;

    private ServerAdapter serverAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.login_page_menu, menu);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        loginViewModel = new ViewModelProvider(requireActivity()).get(LoginViewModel.class);
        bind = FragmentLoginBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        initAppBar();
        initServerListView();
        initNewLoginButton();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (bind == null) return;

            if ((bind.serverInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.login_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    private void initServerListView() {
        bind.serverListRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.serverListRecyclerView.setHasFixedSize(true);

        serverAdapter = new ServerAdapter(this);
        bind.serverListRecyclerView.setAdapter(serverAdapter);
        loginViewModel.getServerList().observe(getViewLifecycleOwner(), servers -> {
            if (!servers.isEmpty()) {
                if (bind != null) bind.noServerAddedTextView.setVisibility(View.GONE);
                if (bind != null) bind.serverListRecyclerView.setVisibility(View.VISIBLE);
                serverAdapter.setItems(servers);
            } else {
                if (bind != null) bind.noServerAddedTextView.setVisibility(View.VISIBLE);
                if (bind != null) bind.serverListRecyclerView.setVisibility(View.GONE);
            }
        });
    }

    public void initNewLoginButton() {

        /* Edge-to-edge fixup */
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) bind.newLoginButton.getLayoutParams();
        final int baseBottomMargin = params.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(bind.newLoginButton, (v, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            params.bottomMargin = baseBottomMargin + systemInsets.bottom;
            v.setLayoutParams(params);
            return windowInsets;
        });

        /* Setup button */
        bind.newLoginButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), LoginActivity.class);
            intent.putExtra("HIDE_TAB_LAYOUT", true);
            intent.putExtra("HIDE_TOPAPPBAR_LAYOUT", false);
            intent.putExtra("SELECT_FRAGMENT", 3);
            startActivity(intent);
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_login) {
            Intent intent = new Intent(requireActivity(), LoginActivity.class);
            intent.putExtra("HIDE_TAB_LAYOUT", false);
            intent.putExtra("HIDE_TOPAPPBAR_LAYOUT", true);
            intent.putExtra("SELECT_FRAGMENT", 0);
            startActivity(intent);
            return true;
        }

        return false;
    }

    @Override
    public void onServerClick(Bundle bundle) {
        Server server = bundle.getParcelable("server_object");
        checkServer(server, firstAddressToTry(server));
    }

    // Credentials are checked against the server before the session is switched, so a server that
    // rejects them leaves the current one signed in instead of logging the user out.
    private void checkServer(Server server, String address) {
        SystemRepository systemRepository = new SystemRepository();
        systemRepository.checkUserCredential(App.getSubsonicClientInstance(server, address), new SystemCallback() {
            @Override
            public void onError(Exception exception) {
                String localAddress = server.getLocalAddress();
                if (localAddress != null && !localAddress.isEmpty() && !localAddress.equals(address)) {
                    // The public address can be out of reach while the local one works.
                    checkServer(server, localAddress);
                    return;
                }

                Context context = getContext();
                if (context != null && isAdded()) {
                    Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onSuccess(String password, String token, String salt) {
                saveServerPreference(server, address);
                new PlaylistRepository().refreshAllPlaylists();
                activity.goFromLogin();
            }
        });
    }

    private String firstAddressToTry(Server server) {
        String inUseAddress = Preferences.getInUseServerAddress();
        if (Objects.equals(inUseAddress, server.getAddress())
                || Objects.equals(inUseAddress, server.getLocalAddress())) {
            return inUseAddress;
        }
        return server.getAddress();
    }

    private void saveServerPreference(Server server, String inUseAddress) {
        Preferences.setServerId(server.getServerId());
        Preferences.setServer(server.getAddress());
        Preferences.setLocalAddress(emptyToNull(server.getLocalAddress()));
        Preferences.setInUseServerAddress(inUseAddress);
        Preferences.setUser(server.getUsername());
        Preferences.setPassword(server.getPassword());
        Preferences.setLowSecurity(server.isLowSecurity());
        Preferences.setClientCert(emptyToNull(server.getClientCert()));
        Preferences.setCustomHeaders(emptyToNull(server.getCustomHeaders()));
        // Derived from another server, if this is a switch without a logout in between.
        Preferences.setToken(null);
        Preferences.setSalt(null);

        App.getSubsonicClientInstance(true);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
