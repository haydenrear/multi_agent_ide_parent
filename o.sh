for x in ./*; do
	if [ -e $x/.git ]; then
		git remote remove origin
		x="${x//.\//}"
		git remote add origin git@github.com:haydenrear/$x.git
		git fetch 
		echo "adding $x"
  	else
	    echo "Could not copy into $file"
	fi
done

