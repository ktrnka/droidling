use warnings;
use LangIDTable;
use IO::Handle;
use Getopt::Long;

STDOUT->autoflush(1);

my $hermit_dave_dir = undef;
my $hc_dir = undef;
my $iso_table = undef;
my $showHelp = 0;

GetOptions(
	'hd-dir=s' => \$hermit_dave_dir,
	'hc-dir=s' => \$hc_dir,
	'iso=s' => \$iso_table,
	'help' => \$showHelp
	);

if ($showHelp)
	{
	print <<HERE;
perl $0 -hd-dir hermit_dave_dir -hc-dir HC_dir -iso ISO-639-2.txt OUTDIR
HERE
	exit;
	}

$hermit_dave_dir =~ s/\/cygdrive\/(\w)/\u$1:/i;
$hc_dir  =~ s/\/cygdrive\/(\w)/\u$1:/i;
$iso_table  =~ s/\/cygdrive\/(\w)/\u$1:/i;
	
my $outdir = pop @ARGV;

# load the ISO table
if (defined $iso_table and -e $iso_table)
	{
	print "Loading ISO table...\n";
	open (my $in, '<:encoding(UTF-8)', $iso_table) or die "$!\n";
	while (my $line = <$in>)
		{
		my ($code3, $code3_alt, $code2, $name_en, $name_fr) = split /\|/, $line;
		
		# take the first of alternatives
		if ($name_en =~ /;/)
			{
			my @alternatives = split /;/, $name_en;
			
			my $pref_index = 0;
			
			if ($alternatives[0] =~ /^(.*\S),\s+(\S.*)$/ and $alternatives[1] eq "$2 $1")
				{
				$pref_index = 1;
				}
			$name_en = $alternatives[$pref_index];
			$name_en =~ s/(^\s+|\s+$)//g;
			}

		my $info = { 'code2' => $code2, 'code3' => $code3, 'name' => $name_en };
		
		$codes->{$code3} = $info;
		$codes->{$code3_alt} = $info;
		$codes->{$code2} = $info;
		$codes->{lc $name_en} = $info;
		$codes->{$name_en} = $info;
		}
	close $in;
	}

### PROCESS THE HERMIT DAVE DIR ###
print "Loading HD files...\n";
opendir DIR, $hermit_dave_dir;
@files = readdir DIR;
closedir DIR;

my @zips = grep /_50K\.zip$/, @files;

foreach my $zip (@zips)
	{
	next unless ($zip =~ /^(\w{2})_50K\.zip$/);
	
	my $code2 = $1;
	my $model = new LangIDTable((defined $codes->{$1} ? $codes->{$1}->{'name'}: $1), $1, (defined $codes->{$1} ? $codes->{$1}->{'code3'}: ''));
	
	print "Training model from $hermit_dave_dir/$zip...\n";
	$model->train("$hermit_dave_dir/$zip");
	
	push @models, $model;
	
	$trained->{$code2}++;
	}

### PROCESS THE HC DIR ###
opendir DIR, $hc_dir;
@files = readdir DIR;
closedir DIR;

# only process the ones that are dirs
@files = grep { -d "$hc_dir/$_" } @files;
foreach my $subdir (@files)
	{
	next unless ($subdir =~ /^([a-z]+)_([a-z]+_)?corpus_(?:torrent_)?\d{4}_\d{2}_\d{2}$/i);
	
	my $name = lc $1;
	my $variation = $2 || '';
	$variation = lc $variation;
	
	# try to look up the info
	my $info = $codes->{$name};
	if (not defined $info)
		{
		print "Skipping dir $subdir; no info found for $name\n";
		next;
		}
	
	if (defined $trained->{$info->{'code2'}})
		{
		print "Already trained $info->{'name'} ($info->{'code2'}), skipping in HC dir\n";
		next;
		}
	
	my $dirpath = "$hc_dir/$subdir";
	opendir(DIR, $dirpath) or die "Failed to load dir $dirpath\n";
	my @files = readdir DIR;
	closedir DIR;
	
	@files = grep /twitter|blogs|newspapers.txt$/i, @files;
	@files = map { "$dirpath/$_" } @files;

	my $model = new LangIDTable($info->{'name'}, $info->{'code2'}, $info->{'code3'});
	print "Training model from " . join(' ', @files) . "...\n";
	
	if ($info->{'code2'} eq 'ja')
		{
		$model->train_hc_nonspace(@files);
		}
	else
		{
		$model->train_hc(@files);
		}
	
	push @models, $model;
	
	$trained->{$info->{'code2'}}++;

	}

### PRUNE/ETC THE MODELS, SAVE THEM ###
foreach my $model (@models)
	{
	print "Differentiating $model->{name}...\n";
	$model->differentiate(grep { $_ != $model } @models);
	}
	
foreach my $model (@models)
	{
	print "Pruning and saving $model->{name}...\n";
	$model->prune(0.99, 50, 50, 50);
	$model->save($outdir);
	}

