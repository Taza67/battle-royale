# WORKFLOW GIT

## Avant de commencer à bosser

Avant de commencer à travaillez, suivez les instructions suivantes :

- Vous déplacez dans ***dev*** :

      git checkout dev

- Téléchargez :

      git fetch dev -> git merge dev

    ou

      git pull

- Retournez dans votre branche :

      git checkout [votre_branche]

- Fusionnez avec ***dev*** et ensuite uploadez (à ce moment, vous ne devez pas avoir de conflits vu que vous n'avez pas commencé à travailler) :

      git merge dev
      git push

- Vous pouvez commencer à travailler.

## Pendant que vous travaillez

Réalisez les tâches et faites des commits régulièrement à chaque fois qu'une tâche est réalisée et répétez la routine suivante :

- Checkez vos modifications :

      git status        

- Chargez vos modifications :

      git add .

- Commitez (Abus de langage) :

      git commit -m "message clair, précis et compact"

- Téléchargez (avant d'upload) :

      git pull

- Réglez les éventuels conflits.

- Uploadez votre travail :

      git push

- Reprenez avec les autres tâches et recommencez cette routine **OU** finalisez et passez à la section suivante.

## Après avoir travaillé

Une fois que vous avez fini de travailler, réalisez ces commandes :

- Vérifiez que vous avez commité vos dernières modifications.

- Vous déplacez dans ***dev*** (cf première section)

- Fusionnez ***dev*** avec votre branche :

      git merge [votre_branche]

- Téléchargez tout ce qui a changé dans ***dev*** pendant que vous bossiez sur votre branche :

      git pull

- Réglez les éventuels conflits.

- Uploadez une fois tous les conflits réglés :

      git push

- Revenez sur votre branche pour pas vous retrouvez à travailler sur dev la prochaine fois.

      git checkout [votre_branche]

## Autres détails

- Pensez toujours à vérifier dans quelle branche vous êtes avec :

      git branch

- Pensez aussi à vérifier sur gitlab les différences de commits. Une trop grande différence sans une bonne explication est mauvais signe.

- Il y a d'autres workflows qui existent. Celui-ci est loin d'être le meilleur. Allez chercher sur internet. Il faut juste que vous en suiviez un.
