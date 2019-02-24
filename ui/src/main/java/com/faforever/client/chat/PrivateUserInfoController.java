package com.faforever.client.chat;

import com.faforever.client.achievements.AchievementService;
import com.faforever.client.achievements.AchievementState;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Game;
import com.faforever.client.i18n.I18n;
import com.faforever.client.play.GameDetailController;
import com.faforever.client.player.Player;
import com.faforever.client.util.IdenticonUtil;
import com.neovisionaries.i18n.CountryCode;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class PrivateUserInfoController implements Controller<Node> {
  private final CountryFlagService countryFlagService;
  private final I18n i18n;
  private final AchievementService achievementService;

  public ImageView userImageView;
  public Label usernameLabel;
  public ImageView countryImageView;
  public Label countryLabel;
  public Label globalRatingLabel;
  public Label leaderboardRatingLabel;
  public Label gamesPlayedLabel;
  public GameDetailController gameDetailController;
  public Pane gameDetailWrapper;
  public Label unlockedAchievementsLabel;
  public Node privateUserInfoRoot;
  public Label globalRatingLabelLabel;
  public Label leaderboardRatingLabelLabel;
  public Label gamesPlayedLabelLabel;
  public Label unlockedAchievementsLabelLabel;

  @SuppressWarnings("FieldCanBeLocal")
  private InvalidationListener rankInvalidationListener;
  @SuppressWarnings("FieldCanBeLocal")
  private InvalidationListener gameInvalidationListener;

  public PrivateUserInfoController(CountryFlagService countryFlagService, I18n i18n, AchievementService achievementService) {
    this.countryFlagService = countryFlagService;
    this.i18n = i18n;
    this.achievementService = achievementService;
  }

  @Override
  public Node getRoot() {
    return privateUserInfoRoot;
  }

  public void initialize() {
    JavaFxUtil.bindManagedToVisible(
      gameDetailWrapper,
      countryLabel,
      gamesPlayedLabel,
      unlockedAchievementsLabel,
      globalRatingLabel,
      leaderboardRatingLabel,
      globalRatingLabelLabel,
      leaderboardRatingLabelLabel,
      gamesPlayedLabelLabel,
      unlockedAchievementsLabelLabel
    );
    onPlayerGameChanged(null);
  }

  public void setChatUser(@NotNull ChatChannelUser chatUser) {
    chatUser.getPlayer().ifPresentOrElse(this::displayPlayerInfo, () -> {
      chatUser.playerProperty().addListener((observable, oldValue, newValue) -> {
        if (newValue != null) {
          displayPlayerInfo(newValue);
        } else {
          displayChatUserInfo(chatUser);
        }
      });
      displayChatUserInfo(chatUser);
    });
  }

  private void displayChatUserInfo(ChatChannelUser chatUser) {
    usernameLabel.textProperty().bind(chatUser.usernameProperty());
    onPlayerGameChanged(null);
    setPlayerInfoVisible(false);
  }

  private void setPlayerInfoVisible(boolean visible) {
    userImageView.setVisible(visible);
    countryLabel.setVisible(visible);
    globalRatingLabel.setVisible(visible);
    globalRatingLabelLabel.setVisible(visible);
    leaderboardRatingLabel.setVisible(visible);
    leaderboardRatingLabelLabel.setVisible(visible);
    gamesPlayedLabel.setVisible(visible);
    gamesPlayedLabelLabel.setVisible(visible);
    unlockedAchievementsLabel.setVisible(visible);
    unlockedAchievementsLabelLabel.setVisible(visible);
  }

  private void displayPlayerInfo(Player player) {
    setPlayerInfoVisible(true);
    CountryCode countryCode = CountryCode.getByCode(player.getCountry());

    usernameLabel.textProperty().bind(player.displayNameProperty());

    userImageView.setImage(IdenticonUtil.createIdenticon(player.getId()));
    userImageView.setVisible(true);

    countryFlagService.loadCountryFlag(player.getCountry()).ifPresent(image -> countryImageView.setImage(image));
    countryLabel.setText(countryCode == null ? player.getCountry() : countryCode.getName());
    countryLabel.setVisible(true);

    rankInvalidationListener = (observable) -> loadReceiverRankInformation(player);
    // FIXME this property won't update when the skill class changes
    JavaFxUtil.addListener(player.ratingProperty(), new WeakInvalidationListener(rankInvalidationListener));
    loadReceiverRankInformation(player);

    gameInvalidationListener = observable -> onPlayerGameChanged(player.getGame());
    JavaFxUtil.addListener(player.gameProperty(), new WeakInvalidationListener(gameInvalidationListener));
    onPlayerGameChanged(player.getGame());

    JavaFxUtil.bind(gamesPlayedLabel.textProperty(), player.numberOfGamesProperty().asString());

    populateUnlockedAchievementsLabel(player);
  }

  private CompletableFuture<CompletableFuture<Void>> populateUnlockedAchievementsLabel(Player player) {
    return achievementService.getAchievements()
      .thenApply(achievements -> {
        int totalAchievements = achievements.size();
        return achievementService.getPlayerAchievements(player.getId())
          .thenAccept(playerAchievements -> {
            long unlockedAchievements = playerAchievements.stream()
              .filter(playerAchievement -> playerAchievement.getState() == AchievementState.UNLOCKED)
              .count();

            Platform.runLater(() -> unlockedAchievementsLabel.setText(
              i18n.get("chat.privateMessage.achievements.unlockedFormat", unlockedAchievements, totalAchievements))
            );
          })
          .exceptionally(throwable -> {
            log.warn("Could not load achievements for player '" + player.getId(), throwable);
            return null;
          });
      });
  }

  private void onPlayerGameChanged(Game newGame) {
    gameDetailController.setGame(newGame);
    gameDetailWrapper.setVisible(newGame != null);
  }

  private void loadReceiverRankInformation(Player player) {
    // FIXME display icon instead, one per leaderboard
    Platform.runLater(() -> globalRatingLabel.setText(i18n.get("chat.privateMessage.ratingFormat", player.getRating())));
  }
}
